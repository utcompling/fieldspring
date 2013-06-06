package opennlp.fieldspring.tr.tpp

import java.util.ArrayList

import scala.collection.JavaConversions._

class ACOTPPSolver(factory:GaussianAntFactory, runningPopulation:ArrayList[AntProperty]) extends TPPSolver {

  val NUM_ANTS = 10

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

    //val experFactory = new ConstantAntFactory(0.0, 0.0, 0.0, 0.0)

    /*val factories = Array(new ConstantAntFactory(1.0,1.0,1.0,1.0),
                          new ConstantAntFactory(1.0,5.0,1.0,1.0),
                          new ConstantAntFactory(0.5,1.0,1.0,1.0),
                          new ConstantAntFactory(1.0,2.0,1.0,1.0),
                          new ConstantAntFactory(1.0,0.5,1.0,1.0),
                          new ConstantAntFactory(1.0,1.0,2.0,1.0),
                          new ConstantAntFactory(1.0,1.0,0.5,1.0),
                          new ConstantAntFactory(1.0,1.0,1.0,2.0),
                          new ConstantAntFactory(1.0,1.0,1.0,0.5))*/

    val ants:ArrayList[Ant] = new ArrayList[Ant]
    for(i <- 0 until NUM_ANTS) {
      val newTour = ACOTPPSolver.copyTour(tour)//new ArrayList[MarketVisit]
      //ConstructionTPPSolver.insertIntoTour(newTour, hub, 0, resolvedToponymMentions, tppInstance)
      val newUnresolvedToponymMentions = new scala.collection.mutable.HashSet[ToponymMention]
      for(urtm <- unresolvedToponymMentions) newUnresolvedToponymMentions.add(urtm)
      val newResolvedToponymMentions = new scala.collection.mutable.HashMap[ToponymMention, Market]
      for((topMen, market) <- resolvedToponymMentions) newResolvedToponymMentions.put(topMen, market)

      val ant = /*factories(i % factories.size)*/factory/*experFactory*/.generate(newTour, /*new ArrayList[MarketVisit](tour), */
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

    //val tours = new ArrayList[ArrayList[MarketVisit]]
    var bestTour:ArrayList[MarketVisit] = null
    var cheapestTourCost = Double.PositiveInfinity
    var toursFound = 0

    //println("**********")

    var localIterations = 0

    var done = false
    while(!done) {
      var antIndex = 0
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

        if(ant.unresolvedToponymMentions.size > 0 && ant.unvisitedMarkets.size == 0) {
          println("tours found: "+toursFound)
          println("cheapest: "+cheapestTourCost)
          println("ant id: "+ant.id)
          println("current: "+tppInstance.computeTourCost(ant.tour.toList))
          println("resolved TMs: "+ant.resolvedToponymMentions.size)
          println("unresolved TMs: "+ant.unresolvedToponymMentions.size)
          println("unvisited markets:"+ant.unvisitedMarkets.size)
        }
        
        if(ant.unresolvedToponymMentions.size == 0) {
          //tour = ant.tour
          //done = true
          //tours.add(ant.tour)
          val curTourCost = tppInstance.computeTourCost(ant.tour.toList)
          if(curTourCost < cheapestTourCost || (curTourCost == Double.PositiveInfinity && cheapestTourCost == Double.PositiveInfinity)) {
            bestTour = ACOTPPSolver.copyTour(ant.tour)
            cheapestTourCost = curTourCost

            //println("Found a new best tour of length "+bestTour.size+" and cost "+cheapestTourCost)
          }
          ACOTPPSolver.addPheremones(pheremones, ant.tour, tppInstance)
          toursFound += 1
          //println(toursFound+" tours found")
          if(toursFound >= NUM_ANTS * 2 && bestTour != null/* || (toursFound >= 1 && localIterations > NUM_ANTS * unvisitedMarkets.size)*/) // Stopping criterion
            done = true

          // Start this ant over:
          val newTour = ACOTPPSolver.copyTour(tour)//new ArrayList[MarketVisit]
          val newUnresolvedToponymMentions = new scala.collection.mutable.HashSet[ToponymMention]
          for(urtm <- unresolvedToponymMentions) newUnresolvedToponymMentions.add(urtm)
          val newResolvedToponymMentions = new scala.collection.mutable.HashMap[ToponymMention, Market]
          for((topMen, market) <- resolvedToponymMentions) newResolvedToponymMentions.put(topMen, market)

          ant.tour = newTour
          ant.unvisitedMarkets = new ArrayList[Market](unvisitedMarkets)
          ant.unresolvedToponymMentions = newUnresolvedToponymMentions
          ant.resolvedToponymMentions = newResolvedToponymMentions

          /*println("---")
          println(ant.tour.size)
          println(ant.unvisitedMarkets.size)
          println(ant.unresolvedToponymMentions.size)
          println(ant.resolvedToponymMentions.size)*/
        }

        // Cut ant if it's inefficient, then generate a new one given the current population:
        else if(tppInstance.computeTourCost(ant.tour.toList) > cheapestTourCost) {
                //(1.5 + (unresolvedToponymMentions.size + resolvedToponymMentions.size + tppInstance.markets.size).toDouble/100) * cheapestTourCost) {

          val newTour = ACOTPPSolver.copyTour(tour)//new ArrayList[MarketVisit]
          val newUnresolvedToponymMentions = new scala.collection.mutable.HashSet[ToponymMention]
          for(urtm <- unresolvedToponymMentions) newUnresolvedToponymMentions.add(urtm)
          val newResolvedToponymMentions = new scala.collection.mutable.HashMap[ToponymMention, Market]
          for((topMen, market) <- resolvedToponymMentions) newResolvedToponymMentions.put(topMen, market)

          ants.set(antIndex, ACOTPPSolver.generateAntFromPopulation(ants, newTour, new ArrayList[Market](unvisitedMarkets), newUnresolvedToponymMentions, newResolvedToponymMentions, tppInstance))
        }

        antIndex += 1
      }

      // Evaporate pheremones:
      for((ids, level) <- pheremones) {
        //println(level)
        pheremones.put(ids, level * .999)
      }

      localIterations += 1

      if(localIterations > NUM_ANTS * unvisitedMarkets.size) // Alternative stopping criterion
          done = true
    }

    /*var i = 0
    for(t <- tours) {
      /*println("Tour "+i)
      println("  Length: "+t.length)
      println("  Cost: "+tppInstance.computeTourCost(t.toList))*/
      i += 1
    }*/

    // Of all the tours collected, return the cheapest:
    //tours.map(_.toList).minBy(t => tppInstance.computeTourCost(t))

    // Add this population to the running population:
    for(ant <- ants)
      runningPopulation.add(new AntProperty(ant.a, ant.s, ant.v, ant.d))

    if(bestTour != null)
      bestTour.toList
    else
      null
  }
}

object ACOTPPSolver {

  def addPheremones(pheremones:scala.collection.mutable.HashMap[(Int, Int), Double], tour:ArrayList[MarketVisit], tppInstance:TPPInstance) {

    val tourCost = tppInstance.computeTourCost(tour.toList)
    if(tourCost > 0) {
      val addedPheremone = 1.0 / tppInstance.computeTourCost(tour.toList)
      tour.sliding(2).map(mv => pheremones.put((mv(0).market.id, mv(1).market.id), pheremones((mv(0).market.id, mv(1).market.id)) + addedPheremone))
    }
  }

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

  def generateAntFromPopulation(ants:ArrayList[Ant],
                                tour:ArrayList[MarketVisit],
                                unvisitedMarkets:ArrayList[Market],
                                unresolvedToponymMentions:scala.collection.mutable.HashSet[ToponymMention],
                                resolvedToponymMentions:scala.collection.mutable.HashMap[ToponymMention, Market],
                                tppInstance:TPPInstance): Ant = {

    val aMean = ants.map(_.a).sum/ants.size
    val aStDev = stDev(ants.map(_.a), aMean)
    val sMean = ants.map(_.s).sum/ants.size
    val sStDev = stDev(ants.map(_.s), sMean)
    val vMean = ants.map(_.v).sum/ants.size
    val vStDev = stDev(ants.map(_.v), vMean)
    val dMean = ants.map(_.d).sum/ants.size
    val dStDev = stDev(ants.map(_.d), dMean)

    val factory = new GaussianAntFactory(aMean, aStDev, sMean, sStDev, vMean, vStDev, dMean, dStDev)

    factory.generate(tour, unvisitedMarkets, unresolvedToponymMentions, resolvedToponymMentions, tppInstance)
  }

  def stDev(nums:scala.collection.mutable.Buffer[Double], mean:Double): Double = {
    math.pow(nums.map(n => (n-mean)*(n-mean)).sum/nums.size, 0.5)
  }
}

class Ant(val affinity:Double,
          val laziness:Double,
          val avidity:Double,
          val independence:Double,
          var tour:ArrayList[MarketVisit],
          var unvisitedMarkets:ArrayList[Market],
          var unresolvedToponymMentions:scala.collection.mutable.HashSet[ToponymMention],
          var resolvedToponymMentions:scala.collection.mutable.HashMap[ToponymMention, Market],
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

class AntProperty(val affinity:Double,
                  val laziness:Double,
                  val avidity:Double,
                  val independence:Double) {
  def a = affinity
  def s = laziness
  def v = avidity
  def d = independence
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

  override def toString:String = {
    "a: "+affMean+" ("+affStdev+"); s: "+lazMean+" ("+lazStdev+"); v: "+aviMean+" ("+aviStdev+"); d: "+indMean+" ("+indStdev+")"
  }
}

object GaussianAntFactory {

  def stDev(nums:scala.collection.mutable.Buffer[Double], mean:Double) = ACOTPPSolver.stDev(nums, mean)

  def buildFactory(ants:ArrayList[AntProperty]): GaussianAntFactory = {
    val aMean = ants.map(_.a).sum/ants.size
    val aStDev = stDev(ants.map(_.a), aMean)
    val sMean = ants.map(_.s).sum/ants.size
    val sStDev = stDev(ants.map(_.s), sMean)
    val vMean = ants.map(_.v).sum/ants.size
    val vStDev = stDev(ants.map(_.v), vMean)
    val dMean = ants.map(_.d).sum/ants.size
    val dStDev = stDev(ants.map(_.d), dMean)

    new GaussianAntFactory(aMean, aStDev, sMean, sStDev, vMean, vStDev, dMean, dStDev)
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
