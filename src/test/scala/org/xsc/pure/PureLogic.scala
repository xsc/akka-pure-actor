package org.xsc.pure
import concurrent._

object PureLogic {
  sealed trait Effect
  case class Increment(by: Int = 1) extends Effect
  case class Decrement(by: Int = 1) extends Effect
  case class Print(message: String) extends Effect

  final case class State(value: Int) {
    private def updateValue(f: Int => Int) =
      this.copy(value = f(this.value))

    def update(effect: Effect): State = {
      effect match {
        case Increment(by) => this.updateValue(_ + by)
        case Decrement(by) => this.updateValue(_ - by)
        case Print(_) => this
      }
    }
  }

  def updateState(state: State, effect: Effect): State = {
    state.update(effect)
  }

  sealed trait Action
  final case object Double extends Action
  final case object Fail extends Action
  final case object SayHello extends Action

  def handleAction(state: State, action: Action): (Option[String], List[Effect]) = {
    action match {
      case Double => (None, List(Increment(state.value)))
      case Fail => (Some("It failed."), List.empty)
      case SayHello => (None, List(Print("Hello World!")))
    }
  }

  var printed: Option[String] = None
  var printCount = 0

  def propagateEffect: PartialFunction[Effect, Future[Unit]] = {
    case Print(message) =>
      Future {
        printed = Some(message)
        printCount += 1
      }(ExecutionContext.global)
  }
}
