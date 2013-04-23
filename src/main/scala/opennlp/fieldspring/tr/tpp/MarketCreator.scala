package opennlp.fieldspring.tr.tpp

import opennlp.fieldspring.tr.text._

abstract class MarketCreator(val doc:Document[StoredToken]) {
  def apply:List[Market]
}
