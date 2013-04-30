package opennlp.fieldspring.tr.util

object Average extends App {
  println(args.map(_.toDouble).sum/args.length)
}
