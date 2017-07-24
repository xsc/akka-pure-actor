package org.xsc.pure

object PureLogic {
  sealed trait Effect
  case class Increment(by: Int = 1) extends Effect
  case class Decrement(by: Int = 1) extends Effect

  final case class State(value: Int) {
    private def updateValue(f: Int => Int) =
      this.copy(value = f(this.value))

    def update(effect: Effect): State = {
      effect match {
        case Increment(by) => this.updateValue(_ + by)
        case Decrement(by) => this.updateValue(_ - by)
      }
    }
  }

  def updateState(state: State, effect: Effect): State = {
    state.update(effect)
  }

  sealed trait Action
  final case object Double extends Action
  final case object Fail extends Action

  def handleAction(state: State, action: Action): (Option[String], List[Effect]) = {
    action match {
      case Double => (None, List(Increment(state.value)))
      case Fail => (Some("It failed."), List.empty)
    }
  }

  def propagateEffect: PartialFunction[Effect, Unit] = PartialFunction.empty
}
