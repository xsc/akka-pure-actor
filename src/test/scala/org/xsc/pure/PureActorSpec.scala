package org.xsc.pure

import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import akka.actor.{ Props, ActorSystem, ActorLogging }
import akka.testkit.{ ImplicitSender, TestKit }
import scala.concurrent.duration._
import org.xsc.pure.PureLogic._

// ## Test Actor

object Counter {
  def props(initialValue: Int): Props =
    Props(new Counter(initialValue))
}

class Counter(initialValue: Int)
    extends PureActor[Action, Effect, String, State]
    with ActorLogging {

  val initialState = State(initialValue)
  val updateState = PureLogic.updateState
  val handleAction = PureLogic.handleAction
  val propagateEffect = PureLogic.propagateEffect

  override def wrapReceive(receiveAction: Action => Unit,
                           receiveEffect: Effect => Unit): Receive = {
    case action: Action =>
      log.info(s"action received: $action")
      receiveAction(action)
    case effect: Effect =>
      log.info(s"effect received: $effect")
      receiveEffect(effect)
  }
}

// ## Tests

class PureActorSpec
  extends TestKit(ActorSystem("PureActorSpec"))
  with Matchers
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  def newActor(initialValue: Int) =
    system.actorOf(Counter.props(initialValue))

  "A pure Counter actor" when {
    "receiving a Double command" should {
      lazy val actor = newActor(5)
      "double the current value" in {
        actor ! Double
        actor ! Double
        actor ! PureActor.ProbeState
        expectMsg(1000.millis, State(20))
      }
    }

    "receiving a failing command" should {
      lazy val actor = newActor(0)
      "return the failure to the sender" in {
        actor ! Fail
        expectMsg(1000.millis, "It failed.")
      }
    }
  }
}
