package org.xsc.pure

object PureLogic {
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

  class Handler extends PureActor.Handler[State, Command, Effect, String] {
    def handle(state: State, command: Command): Result = {
      command match {
        case Double => (None, List(Increment(state.value)))
        case Fail => (Some("It failed."), List.empty)
      }
    }
  }
}
