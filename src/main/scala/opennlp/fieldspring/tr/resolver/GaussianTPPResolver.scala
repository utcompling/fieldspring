package opennlp.fieldspring.tr.resolver

import opennlp.fieldspring.tr.tpp._
import opennlp.fieldspring.tr.text._
import opennlp.fieldspring.tr.topo._
import opennlp.fieldspring.tr.util._

import java.util.ArrayList

import scala.collection.JavaConversions._

class GaussianTPPResolver(val dpc:Double,
                          val threshold:Double,
                          val corpus:StoredCorpus,
                          val modelDirPath:String,
                          val doACO:Boolean)
  extends TPPResolver(new TPPInstance(
    //new MaxentPurchaseCoster(corpus, modelDirPath),
    new MultiPurchaseCoster(List(new GaussianPurchaseCoster,//new SimpleContainmentPurchaseCoster,
                                 new MaxentPurchaseCoster(corpus, modelDirPath))),
    new GaussianTravelCoster)) {
    //new SimpleDistanceTravelCoster)) {

  def disambiguate(corpus:StoredCorpus): StoredCorpus = {

    for(doc <- corpus) {

      if(threshold < 0)
        tppInstance.markets = (new GridMarketCreator(doc, dpc)).apply
      else
        tppInstance.markets = (new ClusterMarketCreator(doc, threshold)).apply

      // Apply a TPPSolver
      val solver = if(doACO) new ACOTPPSolver else new ConstructionTPPSolver
      val tour = solver(tppInstance)
      //println(doc.getId+" had a tour of length "+tour.size)
      if(doc.getId.equals("d94")) {
        solver.writeKML(tour, "d94-tour.kml")
      }

      // Decode the tour into the corpus
      val solutionMap = solver.getSolutionMap(tour)

      val docAsArray = TextUtil.getDocAsArrayNoFilter(doc)

      var tokIndex = 0
      for(token <- docAsArray) {
        if(token.isToponym && token.asInstanceOf[Toponym].getAmbiguity > 0) {
          val toponym = token.asInstanceOf[Toponym]
          if(solutionMap.contains((doc.getId, tokIndex))) {
            toponym.setSelectedIdx(solutionMap((doc.getId, tokIndex)))
            //if(toponym.getSelectedIdx >= toponym.getAmbiguity) {
            //  println(tokIndex)
            //  println(toponym.getForm+": "+toponym.getSelectedIdx+" >= "+toponym.getAmbiguity)
            //}
          }
          //else {
          //  println(doc.getId+": "+toponym.getForm)
          //}
        }

        tokIndex += 1
      }

    }

    corpus
  }
}
