package processing

import com.typesafe.config.ConfigFactory
import bloomfilter.mutable.BloomFilter
import java.awt.Canvas
import bloomfilter.CanGenerateHashFrom
import scala.util.Try

/**
 * This is a generic duplicate checker which can be backed
 * by either a mutable set or a bloom filter.
 * 
 * Choosing between the implementation is possible by using the 
 * "processor.bloomfilter.active" config.
 * 
 * This trait supports two main operations: add(elem) and has(elem) which will
 * be delegated to the underlying storage.
 * 
 * This can be used for any type that supports CanGenerateHashFrom (as required by
 * the bloom filter)
 */
object DuplicateChecker {
  
  trait DuplicateChecker[T] {
    def add(item: T): Unit
    def has(item: T): Boolean
    def dispose(): Unit
  }
  
  class SetStorage[T] extends DuplicateChecker[T] {
    var set = scala.collection.mutable.Set[T]()
    def add(item: T) = {
      set.add(item); ()
    }

    def has(item: T): Boolean = set.contains(item)
    def dispose(): Unit = ()
  }
  
  class BloomStorage[T](implicit canGenerateHashFrom: CanGenerateHashFrom[T], val cardinality: Long, val falsepositiveRate: Double) extends DuplicateChecker[T] {
    val bf = BloomFilter[T](expectedElements, falsePositiveRate)
    
    def add(item: T) = bf.add(item)
    def has(item: T): Boolean = bf.mightContain(item)
    def dispose(): Unit = bf.dispose()
  }
  
  val config = ConfigFactory.load()
  
  val useBloom = Try{
    config.getBoolean("processor.bloomfilter.active")
  }.getOrElse(false)
  
  implicit val expectedElements = config.getLong("processor.bloomfilter.expectedCardinality")
  implicit val falsePositiveRate = config.getDouble("processor.bloomfilter.falsepositiveRate")
  
  def apply[T](implicit canGenerateHashFrom: CanGenerateHashFrom[T]): DuplicateChecker[T] = {
    useBloom match {
      case true => new BloomStorage[T]
      case _    => new SetStorage[T]
    }
  }
  
}