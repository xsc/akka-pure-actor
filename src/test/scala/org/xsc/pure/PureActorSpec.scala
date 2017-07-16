package org.xsc.pure

import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import akka.actor.{ Props, ActorSystem, ActorLogging }
import akka.testkit.{ ImplicitSender, TestKit }
import scala.concurrent.duration._

// ## Test Actor

object Counter {
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

  class CounterHandler extends PureActor.Handler[State, Command, Effect, String] {
    def handle(state: State, command: Command): Result = {
      command match {
        case Double => (None, List(Increment(state.value)))
        case Fail => (Some("It failed."), List.empty)
      }
    }
  }

  def props(initialValue: Int): Props =
    Props(new Counter(initialValue))
}

class Counter(initialValue: Int)
extends PureActor[Counter.State, Counter.Command, Counter.Effect, String]
with ActorLogging
{
  import Counter._

  def initial() = State(initialValue)
  def handler() = new CounterHandler()

  override def wrapReceive(handler: Command => Unit): Receive = {
    case command: Command =>
      log.info(s"command received: $command")
      handler(command)
  }
}

// ## Tests

class PureActorSpec
  extends TestKit(ActorSystem("PureActorSpec"))
  with Matchers
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  def newActor(initialValue: Int) =
    system.actorOf(Counter.props(initialValue))

  "A pure Counter actor" when {
    "receiving a Double command" should {
      lazy val actor = newActor(5)
      "double the current value" in {
        actor ! Counter.Double
        actor ! Counter.Double
        actor ! PureActor.ProbeState
        expectMsg(1000.millis, Counter.State(20))
      }
    }

    "receiving a failing command" should {
      lazy val actor = newActor(0)
      "return the failure to the sender" in {
        actor ! Counter.Fail
        expectMsg(1000.millis, "It failed.")
      }
    }
  }
}