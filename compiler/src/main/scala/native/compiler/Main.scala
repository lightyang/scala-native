package native
package compiler

import native.ir._
import native.ir.serialization._
import native.compiler.backend._
import native.compiler.reductions._

object Main extends App {
  def abort(msg: String): Nothing = {
    println(msg)
    System.exit(1)
    throw new Exception("unreachable")
  }
  def run(reds: Seq[Reduction], main: Node) = {
    serializeDotFile(Scope(Map(Name.Main -> main)), "out0.dot")
    var i = 0
    reds.foreach { red =>
      i += 1
      Reduction.run(red, main)
      serializeDotFile(Scope(Map(Name.Main -> main)), s"out$i.dot")
    }
  }

  val (opts, id) = Opt.parse(args.toList) match {
    case (opts, id :: Nil) =>
      (opts, id)
    case (opts, _ ) =>
      abort("Compiler takes a single entry point.")
  }
  val classpath = Opt.get[Opt.Cp](opts).value
  def resolve(n: Name) =
    classpath.resolve(n).getOrElse {
      abort(s"Couldn't resolve $n")
    }
  val main = {
    val moduleName = Name.Module(id)
    val methodName =
      Name.Method(moduleName, "main",
        Vector(Name.Array(Name.Class("java.lang.String"))),
        Name.Prim("unit"))
    val module = resolve(moduleName)
    val method = resolve(methodName)
    val elem = MethodElem(Empty, module, method)
    val call = Call(elem, elem, Seq(module))
    val end = End(Seq(Return(Empty, call, Lit.Unit())))
    Defn.Define(Prim.Unit, Seq(), end, Name.Main)
  }
  run(Seq(
    ModuleLowering,
    ClassLowering,
    UnitLowering,
    JArrayLowering,
    AllocLowering,
    SizeLowering,
    GlobalNaming
  ), main)

  val llvm = ShowLLVM.showSchedule(Schedule(main)).toString
  println(llvm)
  val writer = new java.io.PrintWriter("out.ll")
  writer.write(llvm)
  writer.close()
}