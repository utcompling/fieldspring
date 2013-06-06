package opennlp.fieldspring.tr.tpp

import opennlp.fieldspring.tr.text._

abstract class TravelCoster {

  var doc:Document[StoredToken] = null // In case the travel coster needs to know which document it's on

  def setDoc(doc:Document[StoredToken]) {
    this.doc = doc
  }

  def apply(m1:Market, m2:Market): Double
}
