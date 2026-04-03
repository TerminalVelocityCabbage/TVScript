# TVScript Reference

TVScript is a high-level, object-oriented, statically typed, game or mod scripting language. It uses an indentation-based syntax similar to languages like Python. Its goal is to be optimized for embedding into java game engines, but is licensed under the MIT license, so feel free to write a runtime for your language of choice. The language definition itself does not require that it be implemented in Java; that's just what I use.

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

// Functions are defined with the function keyword
function sayHello():
    //Statement belonging to the block must be indented
    print "hello"

// Functions can take in parameters and return a value
function add(integer a, integer b) -> integer:
    return a + b

//Functions can also have default values, but must be at the end of the parameter list
function doRandomThings(integer a, integer b = 20):
    
    //Scope is limited to blocks
    const integer localConst = 5

    //conditionals are allowed
    if a < localConst:
        print(a)
    else if b > 5:
        print(b)
    else:
        print "fail"

    //You can also use a range for loops
    for 0..10:
        print "printing"

    for 0..10 -> i:
        print(i)

    while a != 0:
        a--

    // Lists are cool
    list[integer] numbers = list[] //Empty array
    list[integer] filled = list[](1, 2, 3)
    integer foo = filled[0] //sets a to 1
    // Multi dimensional arrays are also supported
    list[integer][integer] twoDimensional = list[][]

    for [integer i] in filled::
        print(i):

    // Maps are cooler
    map[string|integer] employeeAges = map[]("brad":20, "samantha":21)
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
trait emitssound:
    default makeSound():
        print "beep"
trait flies:
    fly()

// Inheritance
class Dog < Animal [emitssound, flies]:

    constructor(string breed):
        super(name: breed) // Use super to call parent constructors
  
    //Override default behavior with the override keyword
    override makeSound():
        super.makeSound() // Call parent method (not usually in this context, but like this)
        print "woof" 
  
    //Implement undefined behaviour with the same keyword
    override fly():
        dispatch onDeath(deathMessage: "dogs can't fly")

@EditorHint("This is a hint that appears in the editor")
event DeathEvent:
    string deathMessage
    Color messageColor = Color.RED

on DeathEvent(string: deathMessage):
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
| `this` | The current instance of the class |
| `super` | Calls the parent constructor |
| `override` | Overrides a method |
| `as` | Cast a value to a different type |
| `list` | Defines a list |
| `map` | Defines a map |
| `enum` | Defines an enum |
| `event` | Defines an event |
| `on` | Defines an event handler |
| `dispatch` | Dispatches an event |
| `annotation` | Defines an annotation |
| `pass` | A do nothing statement |

## Operators
The following operators are supported:
| Operator | Description |
| --- | --- |
| `(` `)` | Grouping (Highest Priority) Not really an operator, but let you define precedence |
| `x[index]` | Subsciption |
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
| `"""hello"""` | Tripple Quoted String literal |

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

ModInfo modinfo = modinfo()
```
if paths to a script have conflicting names you can clarify the path or use as in the import
```
some.package.here.ModInfo
some.other.package.here.ModInfo //error, ModInfo already exists

#instead do
some.package.here.ModInfo

ModInfo modinfo = modinfo()
some.other.package.here.ModInfo modinfo = some.other.package.here.modinfo()

#however this is ugly af, so you can also do this
some.package.here.ModInfo
some.other.package.here.ModInfo as OtherModInfo //you can use this if there is no conflict too

ModInfo modinfo = ModInfo()
OtherModInfo modinfo = OtherModInfo()
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

```access transformers
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

## Variables
Variables are defined with a type first, then an identifier, then a value.
```
boolean a = true
integer b = 4
decimal c = 5.0
string d = "Some string"
```
### Inferred Types
Variables can also have their types inferred using the `var` keyword, meaning that if no type is specified, the type will be inferred from the value assigned to the variable. The type of the variable cannot be changed once it is defined event if the type is inferred. The following is equivalent to the definitions above.
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
list[integer] numbersE = [] //Create a totally empty array
var numbers = [] //ERROR, unknown type
list[integer] numbers = [10] //Initial size of 10 none value placeholders
list[integer] filled = [](1, 4, 5, 12, 12) //init the list with some known start values
```
### Accessing and Assigning Values
To access a value in a list, you can index it with square brackets.
```
var integerList = [](1, 2, 3, 4, 5)

integer firstElement = integerList[0] //sets firstElement to 1
integer lastElement = integerList[-1] //sets lastElement to 5

integerList[0] = 10 //List is now (10, 2, 3, 4, 5)
integerList[-1] = 15 //List is now (10, 2, 3, 4, 15)
```
### Properties of lists
```
var exampleList = [](1, 2, 3, 4)

integer size = exampleList.size //sets length to 4
```
### List transformations
```
list[integer] example = [](1, 2, 3, 4, 5)

//You can't set an element in an array if it doesn't exist
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
list[integer] example = [](1, 2, 3, 4, 5)
list[integer] sublist = example[1..3] //sets sublist to (2, 3, 4)
list[integer] sublist = example[1..] //sets sublist to (2, 3, 4, 5)
list[integer] sublist = example[..3] //sets sublist to (1, 2, 3, 4)
```

## Maps
Maps are a generic key value pairing of object or value types. You define a map similarly to a list, but you need two types `map[keyType | valueType]`

### Creating maps
```
map[string|integer] employeeAgesE = [|] //Create and empty map
map[string|integer] employeeAges = [|]("brad": 20, "samantha": 21, "craig": 80) //Some initial values
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
    print("hello")
```
If you need to track the current itteration, you can pass a variable to the loop
```
for [integer i] in 0...10:
    print(i)
```
### While loops
```
while condition:
    print("hello indefinetley")
```
### Iterating over a list
```
for [string value] in list:
    print(value)
```
### Iterating a map
```
for [string key | string value] in map:
    print("{key} = {value}"
```
## Conditions
If statements are used to execute logic based on a condition.
```
if condition:
  pass //If condition evaluates to true this block will be executed
else if condition2: 
  pass //If condition is false and condition2 is true this block will be executed
else
  pass //If condition is false and condition2 is false this block will be executed
```
### Conditional Expressions
AKA Ternary operators
```
boolean value = condition ? ifTrue : elseFalse
```
#### Match Statements
aka switch statements in java
```
match someString:
  "hello": print("matches hello")
  "world": print("matches world")
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
### Variable Parameters
You can define functions that take in a variable number of parameters by using the `...` syntax.
```
function addAll(integer[...] numbers) -> integer:
  integer sum = 0
  for [integer num] in numbers:
    sum += num
  return sum
```
### Calling functions
You call a function by name
```
sayHello() //Prints "hello" to the console
greet(name: "keith") //Prints "greetings keith" to the console
var sum = add(x: 10, y: 20) //sets sum to 30
var sum2 = addAll(numbers: [10, 20, 30]) //sets sum2 to 60
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
  
  constructor(string name, health: integer = 100):
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
  add(vector2d delta) -> vector2d:
    return vector2d(this.x + delta.x, this.y + delta.y)
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
Since a type is not an object, but a multi-part value instead, there is no `new` keyword to create it. Note that type fields are all constants and cannot be modified. That's where operator overloading comes in.
### Operator Overloading
Operator overloading allows you to define custom behavior for operators like `+`, `-`, `*`, `/`, and more. This is done by defining methods with special names that correspond to the operator. For example, to overload the `+` operator, you would define a method named `add` that takes in some arguments and returns a value like you'd expect. To define an operator overload you need to prefix a normal method name by the ``operator`` keyword. All operator overload methods have a `left` and a `right` parameter of the same type as the type and returns the same type as well.
```
type vector2d:
    decimal x
    decimal y
    
    operator add(vector2d right, vector2d left) -> vector2d:
        return new vector2d(left.x + right.x, left.y + right.y)

    //You don't need to specify the parameter types or the return type for operator methods, it's inferred by the compiler
    operator subtract(left, right):
        return new vector2d(left.x - right.x, left.y - right.y)
```
Now you can do things like:
```
vector2d v1 = new vector2d(x: 10, y: 10)
vector2d v2 = new vector2d(x: 5, y: 5)
vector2d sum = v1 + v2 //results in vector2d(x: 15, y: 15)
//However in our above definition you can't do:
vector2d div = v1 / v2 //error, unefined operation (no operator method defined for "/" on type "vector2d")
```

## Inheritance
Inheritance allows you to create a new class that inherits from another class. Currently, TVScript does not allow direct inheritance for types. You can, however, define traits that types can implement (we'll go over those next). You can override methods from the parent class with the `override` method.

```
class Entity:
    string name
    
    onSpawn():
        print("spawned {name}")
  
class Player < Entity:
    //inherited string name field
    integer health = 10
    
    override onSpawn(): //override methods from the parent class
        print("player spawned {name}")

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
class Dude < person:
  sup():
    print("sup")
  ...
class Gurl < person:
  heyy():
    print("heyy")
  ...

Human billy = new Dude("billy")
Human sally = new Gurl("sally")
boolean isPerson = billy instanceof person //true beause dude extends person
billy.sup() //ERROR because the variable billy is of type human; not dude

Dude dudeVar = billy as Dude
dudeVar.sup() //prints "sup"

#Alternativley you can do this
if billy instanceof Dude -> dudeBilly: //Creates a new variable dudeBilly of type Dude
  dudeBilly.sup() //prints "sup"
```

## Enumerations (enum)
Enumerations are what they describe, a set list of allowable values and potentially some associated data
```
enum ServerStatus:
  OFFLINE
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

## Events
Events are defined similarly to classes, but with the `event` keyword. Events typically have an "Event" suffix. Events can carry some data with them defined as fields, just like classes.
```
event PlayerJoinedEvent:
  Player player
```
Events do not really get instantiated; they get dispatched immediately to any listeners or entry points that listen to them. dispatch an event with the dispatch keyword
```
dispatch PlayerJoinedEvent(player: aPlayerObject)
```
Events acts as a sort of entrypoint to the script, and can be listened to by defining a block in the root of the script using the `on` keyword. Any data associated wit the event that you want to use in your event must be specified in the event definition.
```
on PlayerJoinedEvent(Player player): //parameter names must match the names of the fields in the event definition
  print "Welcome to the server {player.name}"
```
Game engines are encouraged to define their own events that are dispatched by the engine and can be listened to by mods.

## Annotations
Annotations don't really have a purpose in the language itself other than that they allow you to flag things. However, for game engine use these may define some behavior in the engine editor that would otherwise not be able to be derived
```
annotation EditorTooltip:
  string tooltip = "no tooltip specified"

//Will use the default value for the tooltip
@EditorTooltip
class vector2d:
  ...

@EditorTooltip("Represents a 3d point in world space")
class vector3d:
  ...
```

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
#Maybe get a player from a database entry
Player? optionalPlayer = getPlayerFromDatabase("playername")
```
Optionals can be evaluated as booleans where false means the value is none by suffixing the variable name with a ? in the if statement:
```
if optionalPlayer ?:
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
  print(player.name())
```

## Generics
Generics are a way to define functionality without requiring a specific type. Lets take one of the most basic examples possible.
```
printNumbers(list[integer] numbers):
    for [integer number] in numbers:
        print number
```
We can call the above function like this:
```
list[integer] numbers = [1, 2, 3]
printNumbers(numbers)
//Prints 1 2 3 to the console
```
But now we can't do this:
```
list[decimal] numbers = [1.1, 2.2, 3.3]
printNumbers(numbers)
//Error no method printNumbers(list[decimal]) found
```
Without generics, we would have to define a new function for each type we want to be able to print, but we can easily generify the above function like this:
```
printNumbers[T & number](list[T] numbers):
    for [T number] in numbers:
        print number
```
Since integer and decimal both have the trait number, they both match the requirements of the generic function. We can reger to this generig type now as T. T is just the standard first generic type name people use in generics, but this can be any identifier you choose, though we encourage you capitalize them and industry standard is just a single letter most of the time.

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
        print "The {this.name} didn't like that, shame on you!"
```
Now any animal that makes a sound can be put in a cage, and when you kick the cage you get notified about how horrible of a person you are. Note that any fields defined on the clas you are extending in your generic constraint are accessible if they are not marked as private, and any methods from the class or traits you constrain with are also accessible if they are not private.

Note that your constraint can only have one super class and as many traits as you want. ``class Cage[T < ParentClass & Trait1 & Trait2 & EtcTraits]:`` If you do not need to access any fields or methods from any parent class or traits you don't have to constrain it, see example below:
```
function returnOddIndexedItems[T](list[T] items):
    list[T] oddIndexedItems = []
    for [integer i] in 1..items.length:
        if i % 2 == 0: oddIndexedItems.add(items[i])
    return oddIndexedItems
```