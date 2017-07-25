package org.xsc.pure

import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import akka.actor.{ Props, ActorSystem, ActorLogging, PoisonPill }
import akka.testkit.{ ImplicitSender, TestKit }
import scala.concurrent.duration._
import org.xsc.pure.PureLogic._

// ## Test Actor

object PersistentCounter {
  def props(initialValue: Int): Props =
    Props(new PersistentCounter(initialValue))
}

class PersistentCounter(initialValue: Int)
    extends PurePersistentActor[Action, Effect, String, State]
    with ActorLogging {
  val persistenceId = "counter"

  lazy val initialState = State(initialValue)
  val updateState = PureLogic.updateState
  val handleAction = PureLogic.handleAction
  val propagateEffect = PureLogic.propagateEffect

  override def wrapReceive(receiveAction: Action => Unit,
                           receiveEffect: Effect => Unit): Receive = {
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

  def newActor(initialValue: Int) =
    system.actorOf(PersistentCounter.props(initialValue))

  "A pure PersistentCounter actor" when {
    "receiving a Double command" should {
      lazy val actor = newActor(5)
      "double the current value" in {
        actor ! Double
        actor ! Double
        actor ! PureActor.ProbeState
        expectMsg(10000.millis, State(20))
        actor ! PoisonPill
      }

      "retain it after restart" in {
        lazy val sameActor = newActor(5)
        sameActor ! PureActor.ProbeState
        expectMsg(10000.millis, State(20))
        actor ! PoisonPill
      }
    }

    "receiving a side-effecting command" should {
      lazy val actor = newActor(5)
      "propagate effects" in {
        assert(PureLogic.printed == None)
        actor ! SayHello
        expectNoMsg(200.millis)
        assert(PureLogic.printed == Some("Hello World!"))
        assert(PureLogic.printCount == 1)
        actor ! PureActor.ProbeState
        expectMsg(10000.millis, State(20))
      }

      "not replay propagated effects after restart" in {
        lazy val sameActor = newActor(5)
        assert(PureLogic.printed == Some("Hello World!"))
        assert(PureLogic.printCount == 1)
        actor ! PureActor.ProbeState
        expectMsg(10000.millis, State(20))
        assert(PureLogic.printed == Some("Hello World!"))
        assert(PureLogic.printCount == 1)
      }
    }

    "receiving a failing command" should {
      lazy val actor = newActor(0)
      "return the failure to the sender" in {
        actor ! Fail
        expectMsg(10000.millis, "It failed.")
      }
    }
  }
}
