package opennlp.fieldspring.tr.tpp

abstract class PurchaseCoster {

  def apply(m:Market, potLoc:PotentialLocation): Double
}
