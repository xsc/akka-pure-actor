package org.xsc.pure

import akka.persistence.PersistentActor

/*
 * Trait for persistent Actor with Command/Effect semantics:
 * - Commands are processed, produce side-effects and a list of pure state
 *   effects.
 * - Pure state effects are persisted and applied to the internal, immutable
 *   state.
 */
trait PurePersistentActor[State <: PureActor.State[Effect, State], Command, Effect, Response]
    extends PersistentActor {

  def initial(): State
  def handler(): PureActor.Handler[State, Command, Effect, Response]

  private var state = initial()
  private def mutateState(effect: Effect): Unit = {
    state = state.update(effect)
  }

  private def handleReceiveCommand(command: Command): Unit = {
    handler.handle(state, command) match {
      case (maybeResponse, effects) =>
        persistAll(effects)(mutateState)
        maybeResponse.foreach(response => deferAsync(response)(sender() ! _))
    }
  }

  // Boilerplate
  protected def wrapReceiveCommand(handler: Command => Unit): Receive
  protected def wrapReceiveRecover(handler: Effect => Unit): Receive

  private def receiveInternal: Receive = {
    case PureActor.ProbeState => deferAsync(state)(sender() ! _)
  }

  private def generateReceiveCommand() =
    wrapReceiveCommand(handleReceiveCommand).orElse(receiveInternal)

  private def generateReceiveRecover() =
    wrapReceiveRecover(mutateState)

  override def receiveCommand: Receive = generateReceiveCommand()
  override def receiveRecover: Receive = generateReceiveRecover()
}
