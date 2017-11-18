package org.xsc.pure

import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import akka.actor.{ Props, ActorRef, ActorSystem, ActorLogging }
import akka.testkit.{ ImplicitSender, TestKit }
import scala.concurrent.duration._
import org.xsc.pure.PureLogic._

// ## Test Actor

object PersistentCounter {
  def props(id: String): Props =
    Props(new PersistentCounter(id))

  final case object Shutdown
}

class PersistentCounter(id: String)
    extends PurePersistentActor[Action, Effect, String, State]
    with ActorLogging {
  val persistenceId = s"counter-$id"

  lazy val initialState = State(0)
  val updateState = PureLogic.updateState
  val handleAction = PureLogic.handleAction
  val propagateEffect = PureLogic.propagateEffect

  override def wrapReceive(receiveAction: Action => Unit,
                           receiveEffect: Effect => Unit): Receive = {
    case PersistentCounter.Shutdown =>
      context.stop(self)
    case action: Action =>
      log.info(s"action received: $action")
      receiveAction(action)
    case effect: Effect =>
      if (recoveryRunning) {
        log.info(s"recovering effect: $effect")
      } else {
        log.info(s"effect received: $effect")
      }
      receiveEffect(effect)
  }
}

// ## Tests

class PurePersistentActorSpec
  extends TestKit(ActorSystem("PurePersistentActorSpec"))
  with Matchers
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

    // ---- Test Actor
  def newActor(id: String) =
    system.actorOf(PersistentCounter.props(id))

  def withActor[T](id: String)(f: ActorRef => T): Unit = {
    val actor = newActor(id)

    // wait for responsiveness
    actor ! PureActor.ProbeState
    expectMsgPF(10000.millis) { case _: State => true }

    // run logic
    f(actor)

    // shutdown
    expectNoMsg(50.millis)
    actor ! PersistentCounter.Shutdown
  }

  def randomId: String =
    s"${java.util.UUID.randomUUID()}"

  // ---- Expectations
  def expectValue(actor: ActorRef, value: Int): Unit = {
    actor ! PureActor.ProbeState
    expectMsgPF(10000.millis) {
      case State(`value`, _, _) => true
    }
  }

  def expectSideEffect(printCount: Int, printString: String): Unit = {
    expectNoMsg(50.millis)
    assert(PureLogic.printed == Some(printString))
    assert(PureLogic.printCount == printCount)
  }

  def expectPrinted(actor: ActorRef, printCount: Int, printString: String): Unit = {
    actor ! PureActor.ProbeState
    expectMsgPF(10000.millis) {
      case State(_, Some(`printString`), `printCount`) => true
    }
  }

  def expectNonePrinted(actor: ActorRef): Unit = {
    actor ! PureActor.ProbeState
    expectMsgPF(10000.millis) {
      case State(_, None, 0) => true
    }
  }


  // ---- Tests
  "A pure PersistentCounter actor" when {
    "receiving stateful actions" should {
      val actorId = randomId

      "adjust the current value" in {
        withActor(actorId) { actor =>
          actor ! Init(5)
          actor ! Double
          actor ! Double
          expectValue(actor, 20)
        }
      }

      "retain it after restart" in {
        withActor(actorId) { actor =>
          expectValue(actor, 20)
        }
      }
    }

    "receiving a side-effecting command" should {
      val actorId = randomId

      "propagate effects" in {
        PureLogic.resetPrinted()
        withActor(actorId) { actor =>
          actor ! SayHello
          expectSideEffect(1, "Hello World!")
          expectValue(actor, 0)
        }
      }

      "not replay propagated effects after restart" in {
        withActor(actorId) { actor =>
          expectSideEffect(1, "Hello World!")
          expectValue(actor, 0)
          expectSideEffect(1, "Hello World!")
        }
      }

      "be able to propagate new effects after restart" in {
        withActor(actorId) { actor =>
          actor ! SayHello
          expectSideEffect(2, "Hello World!")
        }
      }
    }

    "receiving an action producing an ephemeral effect" should {
      val actorId = randomId

      "process it like any other effect" in {
        PureLogic.resetPrinted()
        withActor(actorId) { actor =>
          actor ! SayHello
          expectSideEffect(1, "Hello World!")
          expectPrinted(actor, 1, "Hello World!")
        }
      }

      "not replay it after restart" in {
        withActor(actorId) { actor =>
          expectNonePrinted(actor)
        }
      }

    }

    "receiving a failing command" should {
      val actorId = randomId
      "return the failure to the sender" in {
        withActor(actorId) { actor =>
          actor ! Fail
          expectMsg(10000.millis, "It failed.")
        }
      }
    }
  }
}
