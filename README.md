# akka-pure-actor

__akka-pure-actor__ provides an opinionated `PersistentActor` implementation
based on immutable state and a simple update scheme, similar to [Redux][redux]
and Clojure's [re-frame][reframe].

[redux]: http://redux.js.org/docs/introduction/
[reframe]: https://github.com/Day8/re-frame

## Quickstart

```scala
import org.xsc.pure.{ PureActor, PureState, Handler }
import akka.actor.Receive
```

First, we model the state, as well as the effects we can apply to it:

```scala
sealed trait Effect
case class Increment(by: Int = 1) extends Effect
case class Decrement(by: Int = 1) extends Effect

final case class State(value: Int) extends PureState[Effect, State] {
  private def updateValue(f: Int => Int) =
    this.copy(value = f(this.value))

  def update(effect: Effect): State =
    effect match {
      case Increment(by) => this.updateValue(_ + by)
      case Decrement(by) => this.updateValue(_ - by)
    }
}
```

Then, we model the commands and the respective handler, translating incoming
commands to a response and a list of events:

```scala
sealed trait Command
final object Double extends Command
final object Fail extends Command

class CounterHandler extends Handler[State, Command, Effect, String] {
  override def handle(state: State, command: Command): Result = {
    command match {
      case Double => onlyEffects(Increment(state.value))
      case Fail => onlyResponse("It failed.")
    }
  }
}
```

This handler is the only place where side-effectful interactions should happen,
so this is where you should inject external datasources.

Now, the actual `PureActor` is basically just a set of boilerplate, that you
shouldn't really need to touch any more since all the logic is contained within
the state and the handler.

```scala
class Counter extends PureActor[State, Command, Effect, String] {
  val persistenceId = "counter"
  def initial(): State(0)
  def handler(): new CounterHandler()

  override def wrapReceiveCommand(handler: Command => Unit): Receive = {
    case command: Command => handler(command)
  }

  override def wrapReceiveRecover(handler: Effect => Unit): Receive = {
    case effect: Effect => handler(effect)
  }
}
```

## License

```
MIT License

Copyright (c) 2017 Yannick Scherer

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
