# akka-pure-actor

__akka-pure-actor__ provides an opinionated `PersistentActor` implementation
based on immutable state and a simple update scheme, similar to [Redux][redux]
and Clojure's [re-frame][reframe].

[redux]: http://redux.js.org/docs/introduction/
[reframe]: https://github.com/Day8/re-frame

## Quickstart

```scala
import org.xsc.pure.PurePersistentActor
```

Internally, we have some kind of actor __state__, e.g.:

```scala
final case class State(value: Int = 0) {
    def updateValue(f: Int => Int) =
      this.copy(value = f(this.value))
}
```

A pure actor gets triggered by an __action__, a datastructure representing
complex logic:

```scala
sealed trait Action
final case object Double extends Action
final case object Reset  extends Action
```

These actions translate to __effects__, simple logical parts of the overall
action:

```scala
sealed trait Effect
case class Increment(by: Int = 1) extends Effect
case class Decrement(by: Int = 1) extends Effect

def handleCounterAction(state: State, action: Action): (Option[String], List[Effect]) = {
  action match {
    case Double => (None, List(Increment(state.value)))
    case Reset => (None, List(Decrement(state.value)))
  }
}
```

And these effects have an impact on the actor state, represented by an update
function:

```scala
def updateCounterState(state: State, effect: Effect): State {
  effect match {
    case Increment(by) => state.updateValue(_ + by)
    case Decrement(by) => state.updateValue(_ - by)
  }
}
```

So far, everything is pure – we even assume that all information necessary for
action validation/handling is contained within the state. But at some point, we
might need to alter the world:

```scala
def propagateCounterEffect: PartialFunction[Effect, Unit] = {
  case Increment(by) => persistIncrementToDB(by)
  case Decrement(by) => persistDecrementToDB(by)
}
```

The code to wire all this together is basically just boilerplate that should
change very rarely:

```scala
final class Counter extends PurePersistentActor[Action, Effect, String, State] {
  val persistenceId = "counter"

  lazy val initialState = State()
  val updateState       = updateCounterState
  val handleAction      = handleCounterAction
  val propagateEffect   = propagateCounterEffect

  override def wrapReceive(receiveAction: Action => Unit,
                           receiveEffect: Effect => Unit): Receive = {
    case action: Action => receiveAction(action)
    case effect: Effect => receiveEffect(effect)
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
