package opennlp.fieldspring.tr.resolver

import opennlp.fieldspring.tr.tpp._
import opennlp.fieldspring.tr.text._
import opennlp.fieldspring.tr.topo._
import opennlp.fieldspring.tr.util._

import opennlp.fieldspring.tr.topo.gaz._

import java.util.ArrayList
import java.util.zip._
import java.io._

import scala.collection.JavaConversions._

class GaussianTPPResolver(val dpc:Double,
                          val threshold:Double,
                          val corpus:StoredCorpus,
                          val modelDirPath:String,
                          val articleInfoPath:String,
                          val linkPath:String,
                          val marketProbPath:String,
                          val gazPath:String,
                          val doACO:Boolean,
                          val acoIterations:Int)
  extends TPPResolver(new TPPInstance(
    //new MaxentPurchaseCoster(corpus, modelDirPath),
    new MultiPurchaseCoster(List(new GaussianPurchaseCoster,//new SimpleContainmentPurchaseCoster,
                                 new MaxentPurchaseCoster(corpus, modelDirPath))),
    if(articleInfoPath != null && linkPath != null) new LinkTravelCoster(articleInfoPath, linkPath, GaussianTPPResolver.loadGaz(gazPath))
    else if(marketProbPath != null) new FileTravelCoster(marketProbPath, corpus, dpc)
    else new GaussianTravelCoster)) {
    //new GaussianTravelCoster)) {
    //new SimpleDistanceTravelCoster)) {

  val rand = new scala.util.Random

  def disambiguate(corpus:StoredCorpus): StoredCorpus = {

    if(doACO) {

      val docsToBestTours = new scala.collection.mutable.HashMap[String, (List[MarketVisit], Double)]

      val docsToMarkets = new scala.collection.mutable.HashMap[String, List[Market]]

      val initialFactory = new GaussianAntFactory(1.0, 0.5, 1.0, 0.5, 1.0, 0.5, 1.0, 0.5)
      val initialPopulation = new ArrayList[AntProperty]
      var factory = initialFactory
      var population = initialPopulation

      for(i <- 1 to acoIterations) {

        println("Iteration: "+i)
        println(factory)

        for(doc <- corpus) {

          //println("Doc: "+doc.getId)

          tppInstance.travelCoster.setDoc(doc)

          tppInstance.markets = docsToMarkets.getOrElse(doc.getId, null)
          if(tppInstance.markets == null) {
            //println(tppInstance.purchaseCoster.asInstanceOf[MultiPurchaseCoster].purchaseCosters(1).asInstanceOf[MaxentPurchaseCoster])
            tppInstance.markets =
              if(threshold < 0) (new GridMarketCreator(doc, dpc, tppInstance.purchaseCoster.asInstanceOf[MultiPurchaseCoster].purchaseCosters(1).asInstanceOf[MaxentPurchaseCoster])).apply
              else (new ClusterMarketCreator(doc, threshold, tppInstance.purchaseCoster.asInstanceOf[MultiPurchaseCoster].purchaseCosters(1).asInstanceOf[MaxentPurchaseCoster])).apply
            docsToMarkets.put(doc.getId, tppInstance.markets)
          }

          val solver = new ACOTPPSolver(factory, population)
          
          var tour = solver(tppInstance)

          // Maintain a best tour and minimum cost for this document across all iterations:
          val cost = tppInstance.computeTourCost(tour)
          val prevBestTourAndCost = docsToBestTours.getOrElse(doc.getId, (null, Double.PositiveInfinity))
          if(cost < prevBestTourAndCost._2)
            docsToBestTours.put(doc.getId, (tour, cost))
          else
            tour = prevBestTourAndCost._1


          if(i == acoIterations) {
            val solutionMap = solver.getSolutionMap(tour)
          
            val docAsArray = TextUtil.getDocAsArrayNoFilter(doc)
            var tokIndex = 0
            for(token <- docAsArray) {
              if(token.isToponym && token.asInstanceOf[Toponym].getAmbiguity > 0) {
                val toponym = token.asInstanceOf[Toponym]
                if(solutionMap != null && solutionMap.contains((doc.getId, tokIndex)))
                  toponym.setSelectedIdx(solutionMap((doc.getId, tokIndex)))
                else if(solutionMap == null) // No solution could be found, so back off to random
                  toponym.setSelectedIdx(rand.nextInt(toponym.getAmbiguity))
              }
              
              tokIndex += 1
            }
          }

        }

        if(i < acoIterations) {
          factory = GaussianAntFactory.buildFactory(population)
          population.clear
        }
      }

    }
    else {
      val solver = new ConstructionTPPSolver

      for(doc <- corpus) {

        tppInstance.travelCoster.setDoc(doc)

        if(threshold < 0)
          tppInstance.markets = (new GridMarketCreator(doc, dpc, tppInstance.purchaseCoster.asInstanceOf[MultiPurchaseCoster].purchaseCosters(1).asInstanceOf[MaxentPurchaseCoster])).apply
        else
          tppInstance.markets = (new ClusterMarketCreator(doc, threshold, tppInstance.purchaseCoster.asInstanceOf[MultiPurchaseCoster].purchaseCosters(1).asInstanceOf[MaxentPurchaseCoster])).apply

        val tour = solver(tppInstance)
        val solutionMap = solver.getSolutionMap(tour)

        val docAsArray = TextUtil.getDocAsArrayNoFilter(doc)
        var tokIndex = 0
        for(token <- docAsArray) {
          if(token.isToponym && token.asInstanceOf[Toponym].getAmbiguity > 0) {
            val toponym = token.asInstanceOf[Toponym]
            if(solutionMap != null && solutionMap.contains((doc.getId, tokIndex)))
              toponym.setSelectedIdx(solutionMap((doc.getId, tokIndex)))
            else if(solutionMap == null) // No solution could be found, so back off to random
              toponym.setSelectedIdx(rand.nextInt(toponym.getAmbiguity))
          }
          tokIndex += 1
        }
      }

    }

    corpus
  }


    /*val initialFactory = new GaussianAntFactory(1.0, 0.1, 1.0, 0.1, 1.0, 0.1, 1.0, 0.1)
    val initialPopulation = new ArrayList[AntProperty]
    var factory = initialFactory
    var population = initialPopulation

    for(doc <- corpus) {

      if(threshold < 0)
        tppInstance.markets = (new GridMarketCreator(doc, dpc)).apply
      else
        tppInstance.markets = (new ClusterMarketCreator(doc, threshold)).apply

      // Apply a TPPSolver
      val solver = if(doACO) new ACOTPPSolver(factory, population) else new ConstructionTPPSolver
      val tour = solver(tppInstance)
      //println(doc.getId+" had a tour of length "+tour.size)
      /*if(doc.getId.equals("d94")) {
        solver.writeKML(tour, "d94-tour.kml")
      }*/

      // Decode the tour into the corpus
      val solutionMap = solver.getSolutionMap(tour)

      val docAsArray = TextUtil.getDocAsArrayNoFilter(doc)

      var tokIndex = 0
      for(token <- docAsArray) {
        if(token.isToponym && token.asInstanceOf[Toponym].getAmbiguity > 0) {
          val toponym = token.asInstanceOf[Toponym]
          if(solutionMap.contains((doc.getId, tokIndex))) {
            toponym.setSelectedIdx(solutionMap((doc.getId, tokIndex)))
          }
        }

        tokIndex += 1
      }

    }

    corpus
  }*/
}

object GaussianTPPResolver {

  def loadGaz(gazPath:String): GeoNamesGazetteer = {
    println("Reading serialized gazetteer from " + gazPath + " ...")
    val gis = new GZIPInputStream(new FileInputStream(gazPath))
    val ois = new ObjectInputStream(gis)
    val gnGaz = ois.readObject.asInstanceOf[GeoNamesGazetteer]
    gis.close

    gnGaz
  }
}
