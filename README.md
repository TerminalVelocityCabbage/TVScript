# TVScript Reference

TVScript is a high-level, object-oriented, statically typed, game or mod scripting language. It uses an indentation-based syntax similar to languages like Python. Its goal is to be optimized for embedding into java game engines, but is licensed under the MIT license, so feel free to write a runtime for your language of choice. The language definition itself does not require that it be implemented in Java; that's just what I use.

## Language Design Goals
- **Fast** TVScript is designed to be fast and efficient. Eventually this language will be compiled to JVM bytecode, so it should run just as fast as Java.
- **Easy to learn** TVScript is intended to be used as a beginner's language while being powerful enough that seasoned developers can still make useful and fast software.
- **Easy to embed** TVScript is designed to be embedded into a game engine, but it's also usable as a standalone language.
- **Easy to read while remaining concise** TVScript is designed to be readable by humans, it's language features are verbose where it matters and concise where possible.
- **Strict and Safe** TVScript is designed as a strongly typed language, and aims to reduce runtime errors by warning the developer if they are doing something that is likely to cause problems.


## Example of TVScript
Some people learn better by taking a look at the syntax directly, keep reading for more details.
```ts
// Everything after a "//" is a comment.
// A file is a script

// Optional imports for logic in other scripts
import path.to.script.OtherScript

// Variables are defined with a type first, then the name, then optionally a value
integer a = 10
boolean b = true
decimal c = 1.23
string d = "hello"

// constant variables are defined with the const keyword
const integer e = 12

// inferred types are also allowed
var f = 10 // The compiler will replace with with `integer`
const g = 12 // The compiler will replace with with `const integer`

//The main entrypoint of a script is defined by the main keyword
main:
    sayHello()
    doRandomThings(a: 12)

// Functions are defined with the function keyword
function sayHello():
    //Statements belonging to the block must be indented
    print "hello"

// Functions can take in parameters and return a value
function add(integer a, integer b) -> integer:
    return a + b

//Function parameters can also have default values, this makes them optional as arguments
function doRandomThings(integer a, integer b = 20):
    
    //Scope is limited to blocks
    const integer localConst = 5

    //conditionals are allowed
    if a < localConst:
        print a
    else if b > 5:
        print b
    else:
        print "fail"

    //You can also use a range for loops
    for 0..10:
        print "printing"

    for [integer i] in 0..10:
        print i

    while a != 0:
        a--

    // Lists are cool
    list[integer] numbers = new list[] //Empty list
    list[integer] filled = new list[](1, 2, 3)
    integer foo = filled[0] //sets a to 1
    // Multi dimensional lists are also supported
    list[integer][integer] twoDimensional = new list[][]

    for [integer i] in filled:
        print i

    // Maps are cooler
    map[string|integer] employeeAges = new map[|]("brad": 20, "samantha": 21)
    integer bar = employeeAges["brad"] //sets bar to 20

    for [string name | integer age] in employeeAges:
        // String formatting
        print "{name} is {age} years old"

    match b:
        3: print "three"
        4:
            print "four"
            print "my lucky number!"
        5..10: print "five thru ten"
        default: print "no match"

// Classes
class Animal:
    const string name

    // You can optionally define a constructor to do some initialization
    constructor():
        this.name = "Animal"

enum Color:
    RED
    GREEN
    BLUE

// Traits
trait EmitsSound:
    default makeSound():
        print "beep"
trait Flies:
    fly()

// Inheritance
class Dog < Animal [EmitsSound, Flies]:

    constructor(string breed):
        super(name: breed) // Use super to call parent constructors
  
    //Override default behavior with the override keyword
    override makeSound():
        super.makeSound() // Call parent method (not usually in this context, but like this)
        print "woof" 
  
    //Implement undefined behaviour with the same keyword
    override fly():
        dispatch DeathEvent(deathMessage: "dogs can't fly")

@EditorHint("This is a hint that appears in the editor")
event DeathEvent:
    string deathMessage
    Color messageColor = Color.RED

on DeathEvent(string deathMessage):
    print deathMessage

annotation EditorHint:
    string hint
```

## Identifiers
Identifiers are case-sensitive and must contain only letters `(a to z and A to Z)`, digits `(0 to 9)` and/or underscores `_`.

## Reserved words
The following words are reserved and cannot be used as identifiers:
| Keyword | Description |
| --- | --- |
| `import` | Imports logic from another script |
| `public` | Public visibility modifier |
| `private` | Private visibility modifier |
| `protected` | Protected visibility modifier |
| `mod` | Mod visibility modifier |
| `var` | Defines a variable |
| `const` | Defines a constant variable |
| `integer` | Defines an integer variable |
| `decimal` | Defines a decimal variable |
| `string` | Defines a string variable |
| `boolean` | Defines a boolean variable |
| `function` | Defines a function |
| `return` | Returns from a function |
| `if` | Defines an if statement |
| `else` | Defines an else statement |
| `for` | Defines a for loop |
| `while` | Defines a while loop |
| `match` | Defines a match statement |
| `default` | Defines a default case in a match statement |
| `break` | Breaks out of a loop |
| `continue` | Continues to the next iteration of a loop |
| `print` | Prints to the console |
| `none` | No value |
| `class` | Defines a class |
| `new` | Creates a new instance of a class |
| `trait` | Defines a trait |
| `type` | Defines a type |
| `operator` | Defines an operator overload |
| `this` | The current instance of the class |
| `super` | Calls the parent constructor or method |
| `override` | Overrides a method |
| `instanceof` | Checks if an object is an instance of a type |
| `as` | Cast a value to a different type |
| `list` | Defines a list |
| `map` | Defines a map |
| `enum` | Defines an enum |
| `event` | Defines an event |
| `on` | Defines an event handler |
| `dispatch` | Dispatches an event |
| `annotation` | Defines an annotation |
| `throw` | Throws an error |
| `throws` | Declares that a function throws an error |
| `try` | Defines a try block |
| `catch` | Defines a catch block |
| `async` | Defines an asynchronous function |
| `await` | Suspends execution until an asynchronous function returns |
| `launch` | Launches an asynchronous function without suspending |
| `all` | Used in await blocks for all-or-nothing completion |
| `timeout` | Used in await blocks to set a timeout |
| `pass` | A do nothing statement |

## Operators
The following operators are supported:
| Operator | Description |
| --- | --- |
| `(` `)` | Grouping (Highest Priority) Not really an operator, but let you define precedence |
| `x[index]` | Subscription |
| `x.attribute` | Attribute access |
| `foo()` | Function call |
| `x.method()` | Method call |
| `x instanceof y` | Checks if `x` is an instance of `y` |
| `x * y` | Multiplication |
| `x / y` | Division |
| `x % y` | Remainder (Modulus) |
| `x + y` | Addition |
| `x - y` | Subtraction |
| `x < y` | Less than |
| `x <= y` | Less than or equal to |
| `x > y` | Greater than |
| `x >= y` | Greater than or equal to |
| `x == y` | Equal to |
| `x != y` | Not equal to |
| `x && y` `x and y` | Logical AND |
| `x || y` `x or y` | Logical OR |
| `!` `not` | Logical NOT |
| `condition ? trueExpression : falseExpression` | Ternary operator |
| `x = y` | Assignment |
| `x += y` | Add and assign |
| `x -= y` | Subtract and assign |
| `x *= y` | Multiply and assign |
| `x /= y` | Divide and assign |
| `x %= y` | Modulus and assign |

## Literals
The following literals are supported:
| Literal | Description |
| --- | --- |
| `none` | No value |
| `true`, `false` | Boolean literals |
| `123` | Integer literal |
| `123.456` | Decimal literal |
| `"hello"` | String literal |
| `"""hello"""` | Triple Quoted String literal |

## Script Layouts
Scripts are expected to be organized in a hierarchical filesystem, and as such these visibility modifiers are derived by the scripts location in that filesystem.:
- `public`: anything anywhere has access
- `private`: only this block has access
- `protected`: all scripts in this folder have access
- `mod`: all scripts in this mod have access (game engine specific)
Given the following file structure:
```fs
mods/
  mod1/
    scripts/
      package1/
        script1.tvs
        script2.tvs
      package2/
        script3.tvs
  mod2/
    scripts
      package1/
        script2.tvs
```
classes defined in script1.tvs belong to the mod "mod1" and the package "package1", and thus it's reference will be `mod1.package1.script1.ClassName`.  any subfolders in a package will also map to a new section in the reference separated by a period `.`, to modify a classes visibility you just prefix any definition by the visibility modifier (the visibility modifier MUST come first in the list of modifiers).
`mods/mod1/scripts/package1/script1.tvs`
```
public class ModInfo:
  string gameVersion
  private string hostName
  mod string mainEntrypoint
```
For the above definition any script can access `ModInfo` to create instances of it etc. Any script in the package1 folder can then get the gameVersion from that object, however the hostName field is accessible only to methods defined in the same block as the field (the class definition), similarly only scripts in the mod1 folder can access the mainEntrypoint field.

In a regular environment the mod visibility modifier will never be used, but when embedding this language into a game engine, it's useful.
### Default visibility
By default, all classes, methods, and fields are protected. This means they can only be accessed by scripts in the same package or a subpackage.
Scripts are public, and there is currently no way to make a script private, you control the visibility of members individually.

### Importing functionality
to import some functionality from one script to another, you can use the import keyword at the start of your file
`mods/mod1/scripts/package2/script3.tvs`
```
import mod1.package1.script1.ModInfo

ModInfo modinfo = new ModInfo()
```
if paths to a script have conflicting names you can clarify the path or use as in the import
```
import some.package.here.ModInfo
import some.other.package.here.ModInfo //error, ModInfo already exists

//instead do
import some.package.here.ModInfo

ModInfo modinfo = new ModInfo()
some.other.package.here.ModInfo modinfo2 = new some.other.package.here.ModInfo()

//however this is ugly af, so you can also do this
import some.package.here.ModInfo
import some.other.package.here.ModInfo as OtherModInfo //you can use this if there is no conflict too

ModInfo modinfo3 = new ModInfo()
OtherModInfo modinfo4 = new OtherModInfo()
```

## Comments
Anything after a `//` is a comment. Comments are ignored by the compiler and are only for human readability.
```
//This is a comment
```
You can also define multiline comments like this
```
///
Multiline comment here
///
```

## Built in Types
The following types are built in:
- `none`
- `boolean`
- `integer`
- `decimal`
- `string`
- `list[type]`
- `map[keyType|valueType]`
- `range`
- `function`

### none
The `none` type is used to represent the absence of a value.

### boolean
The `boolean` type is used to represent `true` or `false`.

### integer
The `integer` type is used to represent whole numbers. Equivalent to `int` in Java

### decimal
The `decimal` type is used to represent floating point numbers. Equivalent to `double` in Java. Decimals are required to have a decimal point with a number before and after its definition. `0.1` is allowed, but `.1` is not.

### string
The `string` type is used to represent text. String literals are surrounded by double quotes. `"hello"` multiline strings are also allowed with tripple-quoted syntax. 

```
"""
this is a multiline
string; yay!
"""
```

### list[type]
The `list[type]` type is used to represent a list of values of a specific type. More on this later.

### map[keyType|valueType]
The `map[keyType|valueType]` type is used to represent a map of key value pairs of a specific type. More on this later.

### range
The `range` type is used to represent a range of values. Defined by `x..y` where `x` is the start and `y` is the end of the range. Ranges are inclusive of both ends and are really only used for integers.

### function
Functions are first-class citizens in TVScript. But since this is a somewhat advanced and nuanced feature, we will circle back to them later. 

## Variables
Variables are defined with a type first, then an identifier, then a value.
```
boolean a = true
integer b = 4
decimal c = 5.0
string d = "Some string"
```
### Inferred Types
Variables can also have their types inferred using the `var` keyword, meaning that if no type is specified, the type will be inferred from the value assigned to the variable. The type of the variable cannot be changed once it is defined even if the type is inferred. The following is equivalent to the definitions above.
```
var a = true
var b = 4
var c = 5.0
var d = "Some string"
```
### Constants
Constant values can never be changed once they are defined, and must be assigned when they are defined. You define a constant with the `const` keyword before the type, or if you want to infer the type.
```
const boolean a = true
a = false //error

const b = 4
b = 5 //error
```
## Expressions
Expressions evaluate to a value. They can be any combination of literals, variables, and constants. `4 + 6` is an expression that evaluates to an integer of value `10`. etc.

Expressions can be define inline with curly braces. For example ``string a = "four plus six is {4 + 6}"``
## Statements
Statements are delineated by newlines, but can span to multiple lines if the line ends in an operator. Statements perform actions, and these actions vary by context. For example, a `print` statement prints the following expression to the console as a string.
## Blocks
Blocks are used to group statements together. Blocks are delineated with a `:` and a newline, and separate scopes of variables to that block. Anything belonging to a block must be indented to fall under that block. All blocks must contain at least one statement. The `pass` statement can be used to fulfill this rule without performing any actions.
```
something: //: defines a block (something is not a keyword, this is an example)
    print "hello" //statements belonging to the block must be indented
```
Blocks with only a single statement can be written on one line the above example could be written as:
```
something: print "hello"
```

## Lists
Lists are a generic sequence of object or value types including other lists or maps. Lists are always indexed starting at 0. Negative indices count from the end.
To define a list you use the `list[type]` syntax.
```
list[integer] numbersE = new list[] //Create a totally empty list
var numbers = new list[] //ERROR, unknown type
list[integer] numbers = new list[10] //Initial size of 10 none value placeholders
list[integer] filled = new list[](1, 4, 5, 12, 12) //init the list with some known start values
```
### Accessing and Assigning Values
To access a value in a list, you can index it with square brackets.
```
list[integer] integerList = new list[](1, 2, 3, 4, 5)

integer firstElement = integerList[0] //sets firstElement to 1
integer lastElement = integerList[-1] //sets lastElement to 5

integerList[0] = 10 //List is now (10, 2, 3, 4, 5)
integerList[-1] = 15 //List is now (10, 2, 3, 4, 15)
```
### Properties of lists
```
var exampleList = new list[](1, 2, 3, 4)

integer size = exampleList.size //sets size to 4
```
### List transformations
```
list[integer] example = new list[](1, 2, 3, 4, 5)

//You can't set an element in a list if it doesn't exist
example[5] = 6 //error

//You can add an element to a list like this
example.add(6) //sets example to (1, 2, 3, 4, 5, 6)

//You can insert an element at a specific index
example.insert(2, 15) //sets example to (1, 2, 15, 3, 4, 5, 6)

//You can remove an element at a specific index
integer removed = example.remove(2) //sets example to (1, 2, 3, 4, 5, 6) and returns and sets removed to 15

//You can remove the last element
integer popped = example.pop() //sets example to (1, 2, 3, 4, 5) and returns and sets popped to 6

//You can clear a list
example.clear() //sets example to ()

//You can reverse a list
example.reverse() //sets example to (5, 4, 3, 2, 1)

//You can determine if a list contains a specific value
boolean contains = example.contains(5) //sets contains to true

//You can obtain sublists from a list
list[integer] example2 = new list[](1, 2, 3, 4, 5)
list[integer] sublist = example2[1..3] //sets sublist to (2, 3, 4)
list[integer] sublist2 = example2[1..] //sets sublist2 to (2, 3, 4, 5)
list[integer] sublist3 = example2[..3] //sets sublist3 to (1, 2, 3, 4)
```

## Maps
Maps are a generic key value pairing of object or value types. You define a map similarly to a list, but you need two types `map[keyType | valueType]`

### Creating maps
```
map[string|integer] employeeAgesE = new map[|] //Create and empty map
map[string|integer] employeeAges = new map[|]("brad": 20, "samantha": 21, "craig": 80) //Some initial values
```
### Accessing and Assigning Values
```
integer bradsAge = employeeAges["brad"] //sets variable bradsAge to 20
employeeAges["brad"] = 21 //Updates brad's age in the map to 21
```

## Loops
Loops are used to execute similar logic a number of times or general iteration.

### For Loops
A range is a special type in TVScript so you can define them as an expression in for loops instead of needing to define it like in java
```
for 0..10:
    print "hello"
```
If you need to track the current iteration, you can pass a variable to the loop
```
for [integer i] in 0..10:
    print i
```
### While loops
```
while condition:
    print "hello indefinitely"
```
### Loops as Expressions
Loops can also be used as expressions to assign values to variables.
```
integer sum = 0
sum = for [integer i] in 0..10: sum += i
print sum //prints 55

var sum2 = 0
sum2 = while sum2 < 10: sum2 += 1
print sum2 //prints 10
```
### Iterating over a list
```
for [string value] in someList:
    print value
```
### Iterating a map
```
for [string key | string value] in someMap:
    print "{key} = {value}"
```
## Conditions
If statements are used to execute logic based on a condition.
```
if condition:
  pass //If condition evaluates to true this block will be executed
else if condition2: 
  pass //If condition is false and condition2 is true this block will be executed
else:
  pass //If condition is false and condition2 is false this block will be executed
```
### Conditional Expressions
Conditions can be used as expressions to assign values to variables.
```
integer value = if condition:
  trueValue
else:
  falseValue
```
Or you can use Ternary operators
```
boolean value = condition ? trueValue : falseValue
```
#### Match Statements
aka switch statements in java
```
match someString:
  "hello": print "matches hello"
  "world": print "matches world"
```
#### Match expressions
```
string value = match someInteger:
  1: "opcode 1"
  2: "opcode 2"
  3, 4: "opcode 3 or 4" //separate matches with commas if needed
  5..9: "opcodes 5 thru 9" //for numbers you can use a range
  default: "no match found" //If nothing is found this will be the value
```

## Functions
Functions are blocks of code that can be called as a statement in other parts of the code.
```
function sayHello():
  print "hello"
```
functions can also take in parameters and be passed arguments. All parameters must define a type:
```
function greet(string name):
  print "greetings {name}!"
```
functions can also be used as expressions by returning a value. The return type must be specified if a return is used.
```
function add(integer a, integer b) -> integer:
  return a + b
```
If no return type is specified, the function is assumed to return `void`. You can specify this as the return type if you feel so inclined, but it is not required. In our earlier example: ``function greet(string name):`` is equivalent to ``function greet(string name) -> void:``.
### Calling functions
You call a function by name
```
sayHello() //Prints "hello" to the console
greet(name: "keith") //Prints "greetings keith" to the console
var sum = add(a: 10, b: 20) //sets sum to 30
```

## Classes
Classes are like templates for objects in your scripts. They hold some data and let you operate on that data. You can define classes with the `class` keyword followed by the class name. Class names are usually capitalized.
```
class Player:
  string name
```
### Creating Objects
To create an instance of an object you call its constructor like this:
```
Player player = new Player(name: "joe")
```
### Custom Constructors
You can define custom constructors for your classes with the constructor keyword.
```
class Player:
  string name
  integer health
  
  constructor(string name, integer health = 100):
    this.name = name
    this.health = health != none ? health : 100
```
### The this keyword
The `this` keyword is used to refer to the current object. You might have seen it used in the constructor above. It just refers to the current object.
### Methods
Methods are like functions that belong to a class.
```
class Vector2d:
  decimal x
  decimal y

  //Adds a vector to this vector and returns a new value
  //Note that methods are not functions per-se, so we don't use the function keyword here
  add(Vector2d delta) -> Vector2d:
    return new Vector2d(x: this.x + delta.x, y: this.y + delta.y)
```
### Default values
This allows you to define an optional default value for a field when it is not set in the constructor.
```
class Vector2d:
  decimal x = 0
  decimal y = 0
```

## Types
A type sounds like the same things as a class, but it has a few nuances for advanced users. Defining a type essentially registers a new primitive type that can be used like any other primitive type with operator overloads. These are used for storing and manipulating data in your scripts directly instead of in containers like classes. You define a type a lot like a class with the `type` keyword. Lets redefine Vector2D as a type:
```
type vector2d: //type names are usually lowercase to differentiate them from classes
  decimal x
  decimal y
```
By itself, this definition doesn't provide many benefits aside from how they can be initialized. You can initialize a type like this:
```
vector2d v = new vector2d(x: 10, y: 10)
```
You initialize a type using the `new` keyword, just like a class. Note that type fields are all constants and cannot be modified. That's where operator overloading comes in.
### Operator Overloading
Operator overloading allows you to define custom behavior for operators like `+`, `-`, `*`, `/`, and more. This is done by defining methods with special names that correspond to the operator. For example, to overload the `+` operator, you would define a method named `add` that takes in some arguments and returns a value like you'd expect. To define an operator overload you need to prefix a normal method name by the ``operator`` keyword. All operator overload methods have a `left` and a `right` parameter of the same type as the type and returns the same type as well.
```
type vector2d:
    decimal x
    decimal y
    
    operator add(vector2d left, vector2d right) -> vector2d:
        return new vector2d(x: left.x + right.x, y: left.y + right.y)

    //You don't need to specify the parameter types or the return type for operator methods, it's inferred by the compiler
    operator subtract(left, right):
        return new vector2d(x: left.x - right.x, y: left.y - right.y)
```
Now you can do things like:
```
vector2d v1 = new vector2d(x: 10, y: 10)
vector2d v2 = new vector2d(x: 5, y: 5)
vector2d sum = v1 + v2 //results in new vector2d(x: 15, y: 15)
//However in our above definition you can't do:
vector2d div = v1 / v2 //error, unefined operation (no operator method defined for "/" on type "vector2d")
```

## Inheritance
Inheritance allows you to create a new class that inherits from another class. Currently, TVScript does not allow direct inheritance for types. You can, however, define traits that types can implement (we'll go over those next). You can override methods from the parent class with the `override` method.

```
class Entity:
    string name
    
    onSpawn():
        print "spawned {name}"
  
class Player < Entity:
    //inherited string name field
    integer health = 10
    
    override onSpawn(): //override methods from the parent class
        print "player spawned {name}"

...

Entity entity = new Entity(name: "joe")
Player player = new Player(name: "momma")

print entity.name //prints "joe"
print player.name //prints "momma"
print player.health //prints 10
print entity.health //error, undefined field (Entity does not have a field named "health")
```

## Traits
Traits are like classes, but they just define some behaviour that is expected of implementing classes. They usually don't have any behaviour of their own, but might.
```
trait EmitsSound:
  playSound()
  
trait CanDie:
    default checkForDeath(integer health):
        if health <= 0:
            print "dead"
```
Classes and types can implement any number of traits that are applicable to that class or type. A class can only extend one other class. All methods defined in all traits implemented by a class MUST be defined in the class. Exceptions to this are default methods of the trait. You only need to override those if you want to.
```
class Player < Entity [EmitsSound, CanDie]:
    
    //Even though there was no default functionality, you still need to override the parent method.
    override playSound():
        print "beep"
        
    override checkForDeath(integer health):
        //If you don't want to override the default functionality, just add to it you can just call the original method like this
        super.checkForDeath(health)
        print "your score was {score}"
```
Good design would prevent this, but if two traits are implemented by a class, and those traits have methods of the same name, the implementing class is REQUIRED to override that method since otherwise the method will be ambiguous. If you need to call the super method, you will need to clarify it by including the interface name in the super call ``TraitName.super.whateverMethodNameHere()``

## Type comparison and conversions
```
class Human:
  const string name
class Dude < Human:
  constructor(string name): super(name: name)
  sup():
    print "sup"
  ...
class Gurl < Human:
  constructor(string name): super(name: name)
  heyy():
    print "heyy"
  ...

Human billy = new Dude(name: "billy")
Human sally = new Gurl(name: "sally")
boolean isHuman = billy instanceof Human //true because dude extends human
billy.sup() //ERROR because the variable billy is of type Human; not Dude

Dude dudeVar = billy as Dude
dudeVar.sup() //prints "sup"

//Alternatively you can do this
if billy instanceof Dude -> dudeBilly: //Creates a new variable dudeBilly of type Dude
  dudeBilly.sup() //prints "sup"
```

## Enumerations (enum)
Enumerations are what they describe, a set list of allowable values and potentially some associated data
```
enum ServerStatus:
  OFFLINE //Enumeration fields are always capitalized
  ONLINE

if getServerStatus() == ServerStatus.OFFLINE:
  print "shutting down server"
```
You can also create enum constructors if you want to carry some more data with the enum other than just its name:
```
enum HorizontalLayout(integer direction): //fields will be automatically generated. Note that enum fields are always constant, and all fields must be defined.
  LEFT(-1)
  MIDDLE(0)
  RIGHT(1)
  
print HorizontalLayout.LEFT.direction
```

## Executing scripts
The primary way to execute a script is through it's main entrypoint, however there are more than one entrypoint type. The next section goes over events, which are a special type of entrypoint that are dispatched by an embedded engine. The main entrypoint however is defined as follows:
```
main(list[string] arguments):
    for [string arg] in arguments:
        print arg
```
This takes in some console arguments and prints them to the console. If you don't have any console arguments, you can omit the parenthesis entirely.
```
main:
    pass
```

## Events
Events are defined similarly to the main entrypoint, but with the `event` keyword. Events typically have an "Event" suffix. Events can carry some data with them defined as fields, just like classes.
```
event PlayerJoinedEvent:
  Player player
```
Events do not need the `new` keyword and are dispatched immediately to any listeners or entry points. Dispatch an event with the `dispatch` keyword.
```
dispatch PlayerJoinedEvent(player: aPlayerObject)
```
Events act as a sort of entrypoint to the script, and can be listened to by defining a block in the root of the script using the `on` keyword. Any data associated with the event that you want to use in your event must be specified in the event definition.
```
on PlayerJoinedEvent(Player player): //parameter names must match the names of the fields in the event definition
  print "Welcome to the server {player.name}"
```
Game engines are encouraged to define their own events that are dispatched by the engine and can be listened to by scripts.
### Pattern matching in Events
Events can be dispatched with a pattern match. This lets you filter before any code in the event block is executed.

Let's take a player death event, maybe you only want to listen to deaths from a specific cause. Just clarify the event field with a known value that you're searching for, and the block of code will only be executed if the event matches that value.
```
class Player:
    string name
    integer level
    ...

enum DeathReason:
    STABBED
    SHOT
    SOMETHING_ELSE
    
class Cause:
    DeathReason reason
    Player killer
    ...

on PlayerDeathEvent(Player player, Cause cause: cause.reason == DeathReason.STABBED):
    print "{player.name} was killed by a knife!"
```
You can also chain together multiple expressions as long as they can evaluate to a boolean.
```
on PlayerDeathEvent(Player player, Cause cause: cause.reason == DeathReason.STABBED && cause.killer.level < 10):
    print "{player.name} was killed by a knife by {cause.killer.name}, that's embarrasing since they're only level {cause.killer.level}"
```

## Annotations
Annotations don't really have a purpose in the language itself other than that they allow you to flag things. However, for game engine use these may define some behavior in the engine editor that would otherwise not be able to be derived
```
annotation EditorTooltip:
  string tooltip = "no tooltip specified"

//Will use the default value for the tooltip
@EditorTooltip
type vector2d:
  ...

@EditorTooltip("Represents a 3d point in world space")
type vector3d:
  ...
```

# Danger zone
Beyond this point are advanced features not recommended for beginner use. If you're just getting started, I would recommend you skip this section. If you've been programming for a while, or have used other languages before keep reading; there is a lot of juicy stuff beyond.

## Optionals
Optionals are a way to define an object as potentially being none.

#### Defining optionals
Optionals in tvscript are defined by suffixing the type with ``?``
```
class Person:
  string firstName
  string? middleName //The middle name is optional
  string lastName
```
#### Accessing optional data
```
//Maybe get a player from a database entry
Player? optionalPlayer = getPlayerFromDatabase(name: "playername")
```
Optionals can be evaluated as booleans where false means the value is none by suffixing the variable name with a ? in the if statement:
```
if optionalPlayer?:
  print optionalPlayer.firstName
```
If you don't care if the value is none, you can just use the value directly and return none if it is none
```
string name = optionalPlayer?.firstName
```
You can also set a value to something if the optional is set or use a default using || and &&
```
//Evaluates to "no name" if player is none
string name = player?.firstName || "no name"
//results in none if player is none or the name of the player if player is set
string name = player && player.middleName
```
You can also unwrap an optional in conditionals
```
if optionalPlayer ? player:
  print player.name
```

## Functions as values
Functions are first-class citizens in tvscript. This means that they act similarly to other values in the language.

Functions can be assigned to variables
```
const squareFunction = function square(integer num) -> integer:
    return num * num
```
Functions can be parameters of other functions
```
function apply(list[integer] numbers, function funcArg(integer num) -> integer):
    list[integer] newNumbers = new list[]
    for [integer num1] in numbers:
        newNumbers.add(funcArg(num: num1))
    return newNumbers
```
Functions can be passed as arguments to other functions
```
list[integer] numbers = new list[](1, 2, 3, 4)
list[integer] squareNumbers = apply(numbers: numbers, funcArg: squareFunction) //sets squareNumbers to (1, 4, 9, 16)
```
Optionally, you can pass an inline function as a parameter as long as it is a single statement. Inline functions are not required to be named.
```
list[integer] numbers = new list[](1, 2, 3, 4)
list[integer] squareNumbers = apply(numbers: numbers, funcArg: (integer num) -> num * num) //sets squareNumbers to (1, 4, 9, 16)
```
Functions can be defined inside other blocks or functions, their scope is limited to the block they are defined in.
```
function squareList(list[integer] numbers) -> list[integer]:
    
    //Inline function
    function square(integer num) -> integer:
        return num * num
    
    list[integer] newNumbers = new list[]
    for [integer num] in numbers: 
        newNumbers.add(square(num))
        
    return newNumbers
```
Functions can return functions
```
function makeMultiplier(integer factor) -> (integer x) -> integer:
  return (integer x) -> x * factor

const double = makeMultiplier(factor: 2)
const result = double(x: 5) // 10
```
You can store functions in maps and lists
```
map[string | (integer num) -> integer] operations = new map[|](
  "square": squareFunction, //Function variable
  "double": (integer num) -> integer: return num * 2 //Inline function
)

//Retrieving a function from a map or list can be invoked like any other function
result = operations["double"](num: 5) //10
```
A note about function types: Function parameter names are a part of the function type: ``(integer num) -> integer`` does not equal ``(integer x) -> integer``. Function return types are also part of the type: ``(integer num) -> integer`` does not equal ``(integer num) -> string``.

## Generics
Generics are a way to define functionality without requiring a specific type. Lets take one of the most basic examples possible.
```
function printNumbers(list[integer] numbers):
    for [integer number] in numbers:
        print number
```
We can call the above function like this:
```
list[integer] numbers = new list[](1, 2, 3)
printNumbers(numbers)
//Prints 1 2 3 to the console
```
But now we can't do this:
```
list[decimal] numbers = new list[](1.1, 2.2, 3.3)
printNumbers(numbers)
//Error no method printNumbers(list[decimal]) found
```
Without generics, we would have to define a new function for each type we want to be able to print, but we can easily generify the above function like this:
```
function printNumbers[T < Number](list[T] numbers):
    for [T number] in numbers:
        print number
```
Since integer and decimal both have the trait Number, they both match the requirements of the generic function. We can refer to this generic type now as T. T is just the standard first generic type name people use in generics, but this can be any identifier you choose, though we encourage you capitalize them and industry standard is just a single letter most of the time.

Now that covers how to match a single trait, but what if we want to match multiple traits or even match a class that extends a specific parent class? lets go back to our animal example:
```
trait MakesSound:
  string makeSound()

class Animal:
    string name
    constructor(string name):
        this.name = name
  
class Dog < Animal:
    constructor(string dogName): super(name: dogName)
    override makeSound(): bark()
    bark(): print "woof"
  
class Cat < Animal:
    constructor(string catName): super(name: catName)
    override makeSound(): meow()
    meow(): print "meow"
```
Let's implement a generic cage class which can house any animal that makes sounds:
```
class Cage[T < Animal & MakesSound]:
    
    Animal animal
    
    constructor(Animal animal): this.animal = animal
    
    kickCage():
        print "kicking cage..."
        animal.makeSound()
        print "The {animal.name} didn't like that, shame on you!"
```
Now any animal that makes a sound can be put in a cage, and when you kick the cage you get notified about how horrible of a person you are. Note that any fields defined on the class you are extending in your generic constraint are accessible if they are not marked as private, and any methods from the class or traits you constrain with are also accessible if they are not private.

Note that your constraint can only have one super class and as many traits as you want. ``class Cage[T < ParentClass & Trait1 & Trait2 & EtcTraits]:`` If you do not need to access any fields or methods from any parent class or traits you don't have to constrain it, see example below:
```
function returnOddIndexedItems[T](list[T] items):
    list[T] oddIndexedItems = new list[]
    for [integer i] in 0..items.size - 1:
        if i % 2 != 0: oddIndexedItems.add(items[i])
    return oddIndexedItems
```

## Errors and handling them
Errors are a way to signal that something went wrong. Sometimes this is expected, and sometimes it is not. When an error occurs, it is important to handle it in a way that makes sense for your program. There are two main ways to handle errors: using try-catch blocks or using error handling functions. First lets look at how to define your own error types and throw them when something goes wrong.

Errors are a special type of class that can be thrown, we define them in nearly the same way as classes, but with the ``error`` keyword and no traits allowed.
```
error FileNotFoundError < Error: //All errors must extend some base error type, Error is the base error type
    string path
```
While most of the time throwing errors is expected to exit the program, sometimes you want to handle errors gracefully. For functions that may result in an error, and that you want to prompt users to be able to gracefully handle those errors, you can mark a function as throwing one or many errors.
```
function readFile(string path) throws FileNotFoundError -> string:
    if !fileExists(path: path):
        throw FileNotFoundError(path: path)
    else:
        return "some data"
```
If you want to suggest handling multiple errors to your user you can define them in a box separated by bars.
```
function readFile(string path) throws [FileNotFoundError | IOException] -> string:
    ...
```
As you can see, we can throw an error by calling the throw keyword followed by the error constructor. We can also mark a function as throwing an error by adding throws to the function definition. You don't have to mark the function as throwing an error if the function handles the error internally. Marking it as throws means that the api expects the user to handle the error themselves. Let's look at how to do that with a try block:
```
try:
    string fileContents = readFile(path: "somefile.txt")
    print "file contents: {fileContents}"
catch FileNotFoundError(string path):
    print "File not found at path: {path}"
```
Note, not all errors need to be caught by uses in the try/catch block, if an error is not caught, it will be propagated up the call stack as normal.

If you'd rather not use a try block you can instead use a try expression `try?` to get a Result[T, Error] object instead, where T is the return type of the function and E is an error being propagated by the function.
```
Result[string, Error] result = try? readFile(path: "a/path/to/a/file.txt")

if result.isOk():
    print "file contents: {result.ok()}"
else:
    match result.error():
        default: //Or handle the errors seprately
            print "error: {result.error()}"
```

## Asynchronous execution
Asynchronous execution is a way to allow the execution of code to continue while other code is being executed.

Consider the following function and event:
```
function getPlayerDataFromDatabase(integer id) -> Player:
  return ...

on PlayerJoinedEvent(Player player):
    PlayerData playerData = getPlayerDataFromDatabase(id: player.id)
    
    print "Player {player.name} joined the server"
```
In this example the function getPlayerDataFromDatabase will block execution of anything else until it returns. This can be a problem if the function takes a long time to complete, as it will prevent other code from running. To avoid this, you can use asynchronous execution to allow the function to run in the background while other code continues to execute.
```
async function getPlayerDataFromDatabase(integer id) -> Player:
  return ...
```
Then in the event listener we can do:
```
on PlayerJoinedEvent(Player player):
    //The await keyword suspends execution of the current thread until the asynchronous function returns without stopping other actions
    PlayerData playerData = await getPlayerDataFromDatabase(id: player.id)
    
    print "Player {player.name} joined the server"
```
Note that the return type of an asynchronous function is a special `Task[T]` type. So when a function is declared as asynchronous, the return type is automatically boxed into the `Task[T]` type. When you use the await keyword the value is automatically unboxed into the type of the function. This means you can do this:
```
on PlayerJoinedEvent(Player player):
    Task[PlayerData] task = getPlayerDataFromDatabase(id: player.id)
    PlayerData playerData = await task
    
    print "Player {player.name} joined the server"
```
If a task is never awaited, you can use the methods defined on `Task[T]` like any other class.

If there is no return value, or you want to just fire and forget the task, you can use the `launch` keyword instead of `await`. This will still execute the function in a non-blocking manner, but doesn't suspend the function or anything like that.
```
async function saveGame():
    ...

//Later
launch saveGame()
```
If an async function results in an error, the error will be propagated to the caller on the current thread.

If you want to concurrently await multiple async functions, you can use an await block. Say you need to fetch data from multiple databases:
```
on PlayerJoinedEvent(Player player):

    //Waits for all the async functions to return before continuing
    //Creates all parameters as variables in the same scope as the await block
    await (Data1 data1, Data2 data2):
        //Note that you don't need to specify await on each function call, it is inferred by the compiler
        data1 = getPlayerDataFromDatabase1(id: player.id)
        data2 = getPlayerDataFromDatabase2(id: player.id)
  
    //Will suspend execution until await block above is complete
    print "{data1} and {data2}"
```
Note that all of these expressions in this block are evaluated in parallel, and that the order of evaluation is not guaranteed.

Also note that not all functions in an await block or a blocking block need to be async functions.

Await blocks also have a few other benefits, such as setting timeouts:
```
await (Data data) timeout 10s:
    data = getSomeData() //Will suspend execution for 10 seconds if getSomeData() does not return within that time.
```
In the above example if getSomeData() does not return within 10 seconds, `data` will be set to none. If you want to set a default value for the variable, just add a default block below it:
```
await (Data data) timeout 10s:
    data = getSomeData()
default:
    data = defaultData
```
By default, there is no timeout, and any values successfully evaluated within the timeout specified will be returned by their evaluated values, if you want an all-or-nothing timeout, add `all` to the await block. This will cause all values be assigned their default value if a timeout is reached:
```
await all (Data1 data1, Data2 data2) timeout 10s:
    data1 = getSomeData1()
    data2 = getSomeData2()
default:
    data1 = defaultData1
    data2 = defaultData2
```
If any of the async functions in the await block throw an error, you can catch it with one or many catch blocks:
```
await (Data data):
    data = getSomeData()
default:
    data = defaultData
catch (ErrorType1 error):
    //OR handle error here
    print error
catch (ErrorType2 error):
    pass //Ignore this type of error
```
The order of the default and catch blocks is not important, the only requirements is that the await block must be first.