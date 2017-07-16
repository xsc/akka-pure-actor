package org.xsc.pure

import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import akka.actor.{ Props, ActorSystem, ActorLogging, PoisonPill }
import akka.testkit.{ ImplicitSender, TestKit }
import scala.concurrent.duration._

// ## Test Actor

object PersistentCounter {
  sealed trait Effect
  case class Increment(by: Int = 1) extends Effect
  case class Decrement(by: Int = 1) extends Effect

  final case class State(value: Int) extends PureActor.State[Effect, State] {
    private def updateValue(f: Int => Int) =
      this.copy(value = f(this.value))

    def update(effect: Effect): State = {
      effect match {
        case Increment(by) => this.updateValue(_ + by)
        case Decrement(by) => this.updateValue(_ - by)
      }
    }
  }

  sealed trait Command
  final object Double extends Command
  final object Fail extends Command

  class PersistentCounterHandler extends PureActor.Handler[State, Command, Effect, String] {
    def handle(state: State, command: Command): Result = {
      command match {
        case Double => (None, List(Increment(state.value)))
        case Fail => (Some("It failed."), List.empty)
      }
    }
  }

  def props(initialValue: Int): Props =
    Props(new PersistentCounter(initialValue))
}

class PersistentCounter(initialValue: Int)
extends PurePersistentActor[PersistentCounter.State,
                            PersistentCounter.Command,
                            PersistentCounter.Effect,
                            String]
with ActorLogging
{
  import PersistentCounter._

  val persistenceId = "counter"
  def initial() = State(initialValue)
  def handler() = new PersistentCounterHandler()

  override def wrapReceiveCommand(handler: Command => Unit): Receive = {
    case command: Command =>
      log.info(s"command received: $command")
      handler(command)
  }

  override def wrapReceiveRecover(handler: Effect => Unit): Receive = {
    case effect: Effect =>
      log.info(s"recovering effect: $effect")
      handler(effect)
  }
}

// ## Tests

class PurePersistentActorSpec
  extends TestKit(ActorSystem("PurePersistentActorSpec"))
  with Matchers
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  def newActor(initialValue: Int) =
    system.actorOf(PersistentCounter.props(initialValue))

  "A pure PersistentCounter actor" when {
    "receiving a Double command" should {
      lazy val actor = newActor(5)
      "double the current value" in {
        actor ! PersistentCounter.Double
        actor ! PersistentCounter.Double
        actor ! PureActor.ProbeState
        expectMsg(10000.millis, PersistentCounter.State(20))
        actor ! PoisonPill
      }

      "retain it after restart" in {
        lazy val sameActor = newActor(5)
        sameActor ! PureActor.ProbeState
        expectMsg(10000.millis, PersistentCounter.State(20))
      }
    }

    "receiving a failing command" should {
      lazy val actor = newActor(0)
      "return the failure to the sender" in {
        actor ! PersistentCounter.Fail
        expectMsg(10000.millis, "It failed.")
      }
    }
  }
}
