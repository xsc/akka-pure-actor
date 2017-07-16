package org.xsc.pure

import akka.actor.Actor

object PureActor {

  trait State[Effect, InternalState <: State[Effect, InternalState]] {
    def update(effect: Effect): InternalState
  }

  trait Handler[InternalState <: State[Effect, InternalState],
                Command,
                Effect,
                Response] {
    type Result = (Option[Response], List[Effect])
    def handle(state: InternalState, command: Command): Result
  }

  final object ProbeState
}

trait PureActor[InternalState <: PureActor.State[Effect, InternalState],
                Command,
                Effect,
                Response]
  extends Actor {

    def initial(): InternalState
    def handler(): PureActor.Handler[InternalState, Command, Effect, Response]

    override def receive: Receive = generateReceive(initial)

    private def generateReceive(state: InternalState): Receive =
      wrapReceive(receiveHandlerCommand(state))
        .orElse(receiveInternal(state))

    private def receiveHandlerCommand(state: InternalState)(command: Command): Unit = {
      handler.handle(state, command) match {
        case (maybeResponse, effects) =>
          val newState = effects.foldLeft(state)(_.update(_))
          context.become(generateReceive(newState))
          maybeResponse.foreach(sender() ! _)
      }
    }

    private def receiveInternal(state: InternalState): Receive = {
      case PureActor.ProbeState => sender() ! state
    }

    protected def wrapReceive(handler: Command => Unit): Receive
}
