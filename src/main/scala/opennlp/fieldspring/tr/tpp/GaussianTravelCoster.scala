package opennlp.fieldspring.tr.tpp

import opennlp.fieldspring.tr.topo._

class GaussianTravelCoster extends TravelCoster {

  val VARIANCE_KM = 1610.0
  val variance = VARIANCE_KM / 6372.8

  def g(x:Double, y:Double) = GaussianUtil.g(x,y)

  val maxHeight = g(0.0,0.0)

  def apply(m1:Market, m2:Market): Double = {
    (maxHeight-g(m1.centroid.distance(m2.centroid)/variance, 0))/maxHeight
  }

  /* old implementation:
  def apply(m1:Market, m2:Market): Double = {
    1.0-g(m1.centroid.distance(m2.centroid)/variance, 0)
  }*/
}

object GaussianTravelCoster extends App {
  val gtc = new GaussianTravelCoster
  //println((gtc.maxHeight-gtc.g(0,0))/gtc.maxHeight)
  //println((gtc.maxHeight-gtc.g(0.25,0))/gtc.maxHeight)
  println((gtc.maxHeight-gtc.g(0.5,0))/gtc.maxHeight)
  println((gtc.maxHeight-gtc.g(1.0,0))/gtc.maxHeight)
  println((gtc.maxHeight-gtc.g(2.0,0))/gtc.maxHeight)
  //println((gtc.maxHeight-gtc.g(3.0,0))/gtc.maxHeight)
}
