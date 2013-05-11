package opennlp.fieldspring.tr.tpp

import java.util.ArrayList

import scala.collection.JavaConversions._

class ACOTPPSolver extends TPPSolver {

  val NUM_ANTS = 2

  def apply(tppInstance:TPPInstance): List[MarketVisit] = {

    if(tppInstance.markets.size == 0) return Nil

    var tour = new ArrayList[MarketVisit]

    val unvisitedMarkets = new ArrayList[Market](tppInstance.markets)

    val unresolvedToponymMentions = getUnresolvedToponymMentions(tppInstance)

    val resolvedToponymMentions = new scala.collection.mutable.HashMap[ToponymMention, Market]

    val bestMarketAndIndexes = ConstructionTPPSolver.chooseBestMarketAndIndexes(unvisitedMarkets, unresolvedToponymMentions, tour, tppInstance)

    val hub = bestMarketAndIndexes._1

    ConstructionTPPSolver.insertIntoTour(tour, hub, 0, resolvedToponymMentions, tppInstance)
    unvisitedMarkets.remove(bestMarketAndIndexes._2)
    resolveToponymMentions(hub, unresolvedToponymMentions)

    val pheremones = new scala.collection.mutable.HashMap[(Int, Int), Double]
    for(i <- tppInstance.markets) {
      for(j <- tppInstance.markets) {
        pheremones.put((i.id, j.id), 1.0)
      }
    }

    val factories = Array(new ConstantAntFactory(1.0,1.0,1.0,1.0),
                          new ConstantAntFactory(1.0,5.0,1.0,1.0)/*
                          new ConstantAntFactory(0.5,1.0,1.0,1.0),
                          new ConstantAntFactory(1.0,2.0,1.0,1.0),
                          new ConstantAntFactory(1.0,0.5,1.0,1.0),
                          new ConstantAntFactory(1.0,1.0,2.0,1.0),
                          new ConstantAntFactory(1.0,1.0,0.5,1.0),
                          new ConstantAntFactory(1.0,1.0,1.0,2.0),
                          new ConstantAntFactory(1.0,1.0,1.0,0.5)*/)

    val ants:ArrayList[Ant] = new ArrayList[Ant]
    for(i <- 0 until NUM_ANTS) {
      val newTour = ACOTPPSolver.copyTour(tour)//new ArrayList[MarketVisit]
      //ConstructionTPPSolver.insertIntoTour(newTour, hub, 0, resolvedToponymMentions, tppInstance)
      val newUnresolvedToponymMentions = new scala.collection.mutable.HashSet[ToponymMention]
      for(urtm <- unresolvedToponymMentions) newUnresolvedToponymMentions.add(urtm)
      val newResolvedToponymMentions = new scala.collection.mutable.HashMap[ToponymMention, Market]
      for((topMen, market) <- resolvedToponymMentions) newResolvedToponymMentions.put(topMen, market)

      val ant = factories(i % factories.size).generate(newTour, /*new ArrayList[MarketVisit](tour), */
                                                       new ArrayList[Market](unvisitedMarkets),
                                                       newUnresolvedToponymMentions,
                                                       newResolvedToponymMentions,
                                                       tppInstance)

      ants.add(ant/*new Ant(1.0, 1.0, 1.0, 1.0, new ArrayList[MarketVisit](tour),
                                           new ArrayList[Market](unvisitedMarkets),
                                           newUnresolvedToponymMentions,
                                           newResolvedToponymMentions,
                                           tppInstance)*/) // copy constructors, right?
    }

    val tours = new ArrayList[ArrayList[MarketVisit]]

    var done = false
    while(!done) {
      for(ant <- ants) {

        if(ant.unresolvedToponymMentions.size > 0) {
          // Move this ant once:
          //println("("+ant.unresolvedToponymMentions.size+","+ant.resolvedToponymMentions.size+")")
          val nextMarketAndIndex = ACOTPPSolver.chooseNextMarketAndIndex(ant, pheremones, tppInstance)
          //println(nextMarketAndIndex._1.id)
          ConstructionTPPSolver.insertIntoTour(ant.tour, nextMarketAndIndex._1, ant.tour.size, ant.resolvedToponymMentions, tppInstance)
          ant.unvisitedMarkets.remove(nextMarketAndIndex._2)
          resolveToponymMentions(nextMarketAndIndex._1, ant.unresolvedToponymMentions)
        }
        
        if(ant.unresolvedToponymMentions.size == 0) {
          //tour = ant.tour
          //done = true
          tours.add(ant.tour)
          if(tours.length == NUM_ANTS)
            done = true
        }
      }
    }

    var i = 0
    for(t <- tours) {
      /*println("Tour "+i)
      println("  Length: "+t.length)
      println("  Cost: "+tppInstance.computeTourCost(t.toList))*/
      i += 1
    }

    // Of all the tours collected, return the cheapest:
    tours.map(_.toList).minBy(t => tppInstance.computeTourCost(t))
  }
}

object ACOTPPSolver {
  def chooseNextMarketAndIndex(ant:Ant, pheremones:scala.collection.mutable.HashMap[(Int, Int), Double], tppInstance:TPPInstance): (Market, Int) = {
    ant.unvisitedMarkets.zipWithIndex.maxBy(j => unnormProb(ant, j._1, tppInstance.travelCoster, pheremones((ant.currentMarket.id, j._1.id))))
  }

  def unnormProb(ant:Ant, otherMarket:Market, tc:TravelCoster, pheremone:Double): Double = {
    math.pow(pheremone, ant.a) * math.pow(math.pow(1.0/tc(ant.currentMarket, otherMarket), ant.s) * math.pow(1.0/ant.purchaseDelta(otherMarket), ant.v), ant.d)
  }

  def copyTour(tour:ArrayList[MarketVisit]) = {
    val newTour = new ArrayList[MarketVisit]
    for(mv <- tour) {
      val newMV = new MarketVisit(mv.market)
      for((topMen, potLoc) <- mv.purchasedLocations)
        newMV.purchasedLocations.put(topMen, potLoc)
      newTour.add(newMV)
    }
    newTour
  }
}

class Ant(val affinity:Double,
          val laziness:Double,
          val avidity:Double,
          val independence:Double,
          val tour:ArrayList[MarketVisit],
          val unvisitedMarkets:ArrayList[Market],
          val unresolvedToponymMentions:scala.collection.mutable.HashSet[ToponymMention],
          val resolvedToponymMentions:scala.collection.mutable.HashMap[ToponymMention, Market],
          val tppInstance:TPPInstance) {

  val id = Ant.nextID

  def purchaseDelta(market:Market): Double = {
    var delta = 0.0

    for((topMen, potLoc) <- market.locations) {
      val newPC = pc(market, potLoc)
      if(unresolvedToponymMentions.contains(topMen)) {
        delta += newPC
      }
      else {
        val oldPC = pc(resolvedToponymMentions(topMen), potLoc)
        if(oldPC > newPC)
          delta += newPC - oldPC
      }
    }
    //println("purchaseDelta: "+delta)
    delta
  }

  def currentMarket = tour.last.market

  def a = affinity
  def s = laziness
  def v = avidity
  def d = independence

  def tc = tppInstance.travelCoster
  def pc = tppInstance.purchaseCoster

  override def equals(other:Any):Boolean = {
    if(!other.isInstanceOf[Ant])
      false
    else {
      val o = other.asInstanceOf[Ant]
      this.id.equals(o.id)
    }
  }

  override def hashCode: Int = this.id
}

object Ant {
  var id = 0
  def nextID = { val toReturn = this.id; this.id += 1; toReturn }
}

abstract class AntFactory {
  def generate(tour:ArrayList[MarketVisit],
               unvisitedMarkets:ArrayList[Market],
               unresolvedToponymMentions:scala.collection.mutable.HashSet[ToponymMention],
               resolvedToponymMentions:scala.collection.mutable.HashMap[ToponymMention, Market],
               tppInstance:TPPInstance): Ant
}

class ConstantAntFactory(val affinity:Double,
                         val laziness:Double,
                         val avidity:Double,
                         val independence:Double) extends AntFactory {

  def generate(tour:ArrayList[MarketVisit],
               unvisitedMarkets:ArrayList[Market],
               unresolvedToponymMentions:scala.collection.mutable.HashSet[ToponymMention],
               resolvedToponymMentions:scala.collection.mutable.HashMap[ToponymMention, Market],
               tppInstance:TPPInstance): Ant = {
    new Ant(affinity, laziness, avidity, independence, tour, unvisitedMarkets, unresolvedToponymMentions, resolvedToponymMentions, tppInstance)
  }
}

class GaussianAntFactory(val affMean:Double, val affStdev:Double,
                         val lazMean:Double, val lazStdev:Double,
                         val aviMean:Double, val aviStdev:Double,
                         val indMean:Double, val indStdev:Double) extends AntFactory {

  val affDist = new UnivariateGaussianDist(affMean, affStdev)
  val lazDist = new UnivariateGaussianDist(lazMean, lazStdev)
  val aviDist = new UnivariateGaussianDist(aviMean, aviStdev)
  val indDist = new UnivariateGaussianDist(indMean, indStdev)

  def generate(tour:ArrayList[MarketVisit],
               unvisitedMarkets:ArrayList[Market],
               unresolvedToponymMentions:scala.collection.mutable.HashSet[ToponymMention],
               resolvedToponymMentions:scala.collection.mutable.HashMap[ToponymMention, Market],
               tppInstance:TPPInstance): Ant = {
    new Ant(affDist.sample, lazDist.sample, aviDist.sample, indDist.sample, tour, unvisitedMarkets, unresolvedToponymMentions, resolvedToponymMentions, tppInstance)
  }
}

class UnivariateGaussianDist(val mean:Double, val stdev:Double) {

  val r = new scala.util.Random

  def apply(x:Double) = {
    (1.0/(stdev * math.pow(2.0 * math.Pi, 0.5))) * math.exp(-math.pow(x-mean,2.0)/(2.0 * stdev * stdev))
  }

  def sample = {
    mean + r.nextGaussian * stdev
  }
}
