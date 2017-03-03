/** This file contains a Node in the sum scheduler set problem
  * An initial solution is created, then transformations are performed using
  * simulated annealing
  */
package chiselutils.algorithms

import Chisel._
import collection.mutable.ArrayBuffer
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.Random

object Node {

  /** Look up an index with a mapping
    */
  private def mapIdx( mapping : Seq[Int], idx : Int ) = {
    if ( idx == -1 )
      idx
    else
      mapping( idx )
  }

  /** Create a node cp coords
    */
  def apply( nodeVals : Seq[Set[Seq[Int]]] ) : Node = {
    val uk = nodeVals.distinct.filter( _.size != 0 )
    val ck = nodeVals.map( uk.indexOf( _ ) )
    apply( uk, ck )
  }

  /** Create a node from compressed cp coords
    */
  def apply( uk : Seq[Set[Seq[Int]]], ck : Seq[Int] ) : Node = {
    // ensure uk is sorted
    val ukSorted = uk.zipWithIndex.sortBy( _._1.hashCode )
    val mapping = ukSorted.map( _._2 ).zipWithIndex.sortBy( _._1 ).map( _._2 )
    val ukOut = ukSorted.map( _._1 )
    val ckOut = ck.map( cki => mapIdx( mapping, cki ) )
    val n = new Node( ukOut, ckOut )
    n
  }

  def apply( uk : List[Set[Seq[Int]]], ck : Seq[Int] ) : Node = {
    Node( uk.to[Seq], ck )
  }

  def apply( uk : Vector[Set[Seq[Int]]], ck : Seq[Int] ) : Node = {
    Node( uk.to[Seq], ck )
  }

  def apply( uk : Vector[Set[Vector[Int]]], ck : Vector[Int] ) : Node = {
    Node( uk.map( s => s.map( v => v.to[Seq] ) ), ck.to[Seq] )
  }

  /** Check if a node satisfies constraint A
    */
  def satisfiesConstraintA( n : Node ) : Boolean = {
    /* Constraint A states that for all cki in ck,
     * cki = ( incr( c_(l,i-1%n) U c_(r,i-1%n) )
     *       AND |c_(l,i-1%n)| != 0 AND |c_(r,i-1%n)| != 0 ) OR
     *       |cki| = 0
     */
    if ( n.numChildren() != 2 && n.numChildren() != 3 )
      return false
    // look for violation of constraint using find
    val violation = n.ck.zipWithIndex.find( cki => {
      n.getUki(cki._1).size != 0 && (
        n.getChildren().find( _.getModIdx( cki._2 ) == -1 ).isDefined || !n.testAddUnion( cki._2 )
      )
    })
    !violation.isDefined
  }

  /** Check if a node satisfies constraint B
    */
  def satisfiesConstraintB( n : Node ) : Boolean = {
    /* Constraint B states that for all cki in ck,
     * cki = incr( c_(l,i-1%n) ) OR incr( c_(r,i-1%n) ) OR |cki| = 0
     */
    if ( n.numChildren() != 1 && n.numChildren() != 2 )
      return false

    // look for violation using find
    val violation = n.ck.zipWithIndex.find( cki => {
      n.getUki(cki._1).size != 0 && !n.testMux( cki._2 )
    })
    !violation.isDefined
  }

  /** Check if a node satisfies constraint C
    */
  def satisfiesConstraintC( n : Node ) : Boolean = {
    /* Constraint C states that the union of all cki in ck has a size of 1
     * and that element has a value of 0 in the first position of the list
     */
    if ( n.numChildren() != 0 )
      return false

    n.testTermination( )
  }

  def satisfiesConstraints( n : Node ) : Boolean = {
    if ( n.isA() )
      return satisfiesConstraintA( n )
    if ( n.isB() )
      return satisfiesConstraintB( n )
    if ( n.isC() )
      return satisfiesConstraintC( n )
    false
  }

  /** Check that there are no extra numbers being put in there
    */
  def isMinimal( n : Node ) : Boolean = {
    val parents = n.getParentSet()
    val nUk = n.getUkNext()
    val nCk = n.getCkNext()
    // find out which mux have n as input and which cycle
    val ckPar = parents.map( p => {
      if ( p.isA() )
        p.ck
      else {
        p.ck.zip( nCk ).map( cks => {
          if ( cks._1 == -1 || cks._2 == -1 )
            -1
          else if ( p.uk( cks._1 ) == nUk( cks._2 ) )
            cks._1
          else
            -1
        })
      }
    })
    !( 0 until nCk.size ).find( idx => {
      nCk(idx) != -1 && !ckPar.find( p => p(idx) != -1 ).isDefined
    }).isDefined
  }

  /** Check that this node satisfies constraints
    * Also check lNode, rNode and parents
   */
  def verifyNode( n : Node ) : Boolean = {
    if ( !satisfiesConstraints( n ) )
      return false

    if ( n.getChildren().find( c => !satisfiesConstraints( c ) ).isDefined )
      return false
    if ( n.getParents().size > 0 ) {
      val violated = n.getParents().find( np => {
        !satisfiesConstraints( np ) || np.isC()
      }).isDefined
      if ( violated )
        return false
      return isMinimal( n )
    }
    true
  }

  def ukPrev( uk : Seq[Set[Seq[Int]]] ) : Seq[Set[Seq[Int]]] = {
    uk.map( uki => uki.map( v => { List( v(0) - 1 ) ++ v.drop(1) }.to[Seq]))
  }
  def ukNext( uk : Seq[Set[Seq[Int]]] ) : Seq[Set[Seq[Int]]] = {
    uk.map( uki => uki.map( v => { List( v(0) + 1 ) ++ v.drop(1) }.to[Seq] ))
  }

  def latency( n : Node ) : Int = n.uk.map( s => s.map( x => x(0) ).max ).max

}

class Node( val uk : Seq[Set[Seq[Int]]], val ck : Seq[Int] ) {

  val nodeSize = ck.size
  private val children = collection.mutable.Set[Node]()
  private val parents = collection.mutable.Set[Node]()
  private var nodeType = -1
  private val available = new AtomicBoolean( false );
  private var nodeChisel : Option[Fixed] = None
  private var muxBool : Option[Bool] = None
  private var validBool = Bool()

  def getModIdx( i : Int ) : Int = { ck( ( i + nodeSize - 1 ) % nodeSize ) }
  def getModSet( i : Int ) : Set[Seq[Int]] = {
    val idx = getModIdx( i )
    if ( idx == -1 )
      return Set[Seq[Int]]()
    uk( idx )
  }

  private def getP0( p : Seq[Int] ) = p.head
  private def incr( p : Seq[Int] ) : Seq[Int] = Vector( p.head + 1 ) ++ p.drop(1)
  private def incr( q : Set[Seq[Int]] )  : Set[Seq[Int]] = q.map( incr(_) )

  def isLocked() = !available.get()
  def unlockNode() = {
    assert( isLocked(), "Trying to unlock available node" )
    available.set( true )
  }
  def lockNode() = {
    available.compareAndSet( true, false )
  }

  def isA() = nodeType == 0
  def isB() = nodeType == 1
  def isC() = nodeType == 2
  def setA() = { nodeType = 0 }
  def setB() = { nodeType = 1 }
  def setC() = { nodeType = 2 }
  def letter() = {
    if ( nodeType == 0 )
      "A"
    else if ( nodeType == 1 )
      "B"
    else if ( nodeType == 2 )
      "C"
    else
      "_"
  }

  def getCkPrev() = { ck.drop(1) ++ ck.take(1) }
  def getCkNext() = { ck.takeRight(1) ++ ck.dropRight(1) }
  def getUki( i : Int ) : Set[Seq[Int]] = {
    if ( i == -1 )
      return Set[Seq[Int]]()
    uk( i )
  }
  def getCki( i : Int ) : Set[Seq[Int]] = getUki( ck( i ) )

  def isAdd() = isA()
  def isAdd2() = isA() && numChildren() == 2
  def isAdd3() = isA() && numChildren() == 3
  def isReg() = isB() && numChildren() == 1
  def isMux() = isB() && numChildren() == 2

  def getUkPrev() : Seq[Set[Seq[Int]]] = { uk.map( uki => uki.map( v => { List( v(0) - 1 ) ++ v.drop(1) }.to[Seq] )) }
  def getUkNext() : Seq[Set[Seq[Int]]] = { uk.map( uki => uki.map( v => { List( v(0) + 1 ) ++ v.drop(1) }.to[Seq] )) }
  def numChildren() : Int = children.size
  def hasChild( n : Node ) : Boolean = { children.contains( n ) }
  def getChildren() : Set[Node] = { children.toSet }
  def removeChild( c : Node ) : Unit = {
    assert( hasChild( c ), "Trying to remove non-child" )
    assert( isLocked(), "Node should be locked to removeChild" )
    c.removeParent( this )
    children -= c
  }
  def removeChildren() : Unit = {
    for ( child <- getChildren() )
      removeChild( child )
  }
  def addChild( n : Node ) : Unit = {
    assert( isLocked(), "Node should be locked to addChild" )
    children += n
    n.addParent( this )
  }
  def addChildren( nS : Iterable[Node] ) : Unit = {
    for ( n <- nS )
      addChild( n )
  }
  def getRandomChild() : (Node, Set[Node]) = {
    val idx = Random.nextInt( children.size )
    val randChild = getChildIter().drop( idx ).next()
    ( randChild, children.toSet - randChild )
  }
  def getRandomChildOrder() : Seq[Node] = {
    Random.shuffle( children.toVector )
  }
  def getOnlyChild() : Node = {
    assert( numChildren() == 1, "Cannot get only child, not 1 child!" )
    getChildIter().next()
  }
  def getChildIter() : Iterator[Node] = {
    children.iterator
  }
  def replaceIfChild( childOld : Node, childNew : Node ) : Boolean = {
    val search = children.find( _ == childOld )
    if ( search.isDefined ) {
      removeChild( childOld )
      addChild( childNew )
    }
    search.isDefined
  }
  def getParents() : Seq[Node] = parents.toVector
  def getParentSet() : Set[Node] = parents.toSet
  def parentsIsEmpty() : Boolean = parents.isEmpty
  def intersectPar( otherP : Set[Node] ) = { otherP.intersect( getParentSet() ) }
  private def addParent( n : Node ) = {
    parents += n
  }
  private def removeParent( n : Node ) = {
    assert( hasParent(n), "Trying to remove non parent " + n )
    parents -= n
  }
  def hasParent( n : Node ) : Boolean = { parents.contains( n ) }

  /** Returns if cki = ( incr( c_(l,i-1%n) U c_(r,i-1%n) )
    */
  def testAddUnion( i : Int ) : Boolean = {
    val childSets = children.toVector.map( child => child.getModSet( i ) )
    val allSet = incr( childSets.reduce( _ ++ _ ) )
    val thisSet = getCki( i )
    if ( childSets.map( _.size ).reduce( _ + _ ) != thisSet.size || allSet.size != thisSet.size )
      return false
    allSet == thisSet
  }

  /** Returns if cki = incr( c_(l,i-1%n) ) or incr( c_(r,i-1%n) )
    */
  def testMux( i : Int ) : Boolean = {
    if ( numChildren() != 1 && numChildren() != 2 )
      return false
    val childSets = children.toVector.map( child => {
      incr( child.getModSet( i ) )
    })
    childSets.find( _ == getCki( i ) ).isDefined
  }

  /** Returns if the union of all cki is size 1 and that set has a size of 1
    * And if the element in that set has it's first element as 0
    */ 
  def testTermination() : Boolean = {
    if ( numChildren() != 0 )
      return false
    if ( uk.size != 1 || uk.head.size != 1)
      return false
    uk.head.find( p => getP0( p ) == 0 ).isDefined
  }

  private def getMuxSwitch( childL : Node, childR : Node ) : Vector[Int] = {
    assert( isMux(), "Must be mux to call muxSwitch" )

    val lUk = childL.getUkNext()
    val lCk = childL.getCkNext()
    val rUk = childR.getUkNext()
    val rCk = childR.getCkNext()
    ck.zip( lCk.zip( rCk ) ).map( cks => {
      // unnecessary to have both but better error checking
      val isL = ( cks._1 != -1 && cks._2._1 != -1 && lUk( cks._2._1 ) == uk( cks._1 ) )
      val isR = ( cks._1 != -1 && cks._2._2 != -1 && rUk( cks._2._2 ) == uk( cks._1 ) )
      val res = {
        if ( isL == isR )
          0
        else if ( isL )
          -1
        else
          1
      }
      res
    }).toVector
  }

  def setChisel( n : Fixed ) = { nodeChisel = Some(n) }
  def getChisel() = { nodeChisel }
  private def treeReduce( conds : Seq[Bool] ) : Bool = {
    if ( conds.size == 0 )
      return Bool(false)
    if ( conds.size == 1 )
      return conds(0)
    val newConds = conds.splitAt( conds.size / 2 )
    treeReduce( newConds._1 ) || treeReduce( newConds._2 )
  }
  def genChisel() : Fixed = {
    if ( nodeChisel.isDefined )
      return nodeChisel.get
    assert( isA() || isB(), "Must set the C nodes before the others can be generated" )
    val updateVal = {
      if ( isA() )
        children.map( child => child.genChisel() ).reduce( _ + _ )
      //else if ( numChildren() == 1 ) should be like this ...
      else if ( isReg() )
        getOnlyChild().genChisel()
      else {
        // otherwise mux ...
        val cntr = RegInit( UInt( 0, log2Up( nodeSize ) ) )
        cntr := cntr + UInt( 1, log2Up( nodeSize ) )
        when ( cntr === UInt( ck.size - 1, log2Up( nodeSize ) ) ) {
          cntr := UInt( 0, log2Up( nodeSize ) )
        }
        val childList = getChildren().toList
        val muxSwitch = getMuxSwitch( childList(0), childList(1) )
        // TODO: use don't cares to simplify logic
        val rIdxs = muxSwitch.zipWithIndex.filter( mi => mi._1 == 1 ).map( _._2 )
        val rCond = treeReduce( rIdxs.map( ri => { cntr === UInt( ri, log2Up( nodeSize ) ) }) )
        muxBool = Some( rCond )
        Mux( muxBool.get, childList(1).genChisel(), childList(0).genChisel() )
      }
    }
    val newReg = RegNext( updateVal )
    // should add a global pause/cont?
    // newReg := Mux( validBool, updateVal, newReg )
    nodeChisel = Some( newReg )
    nodeChisel.get
  }

  def getMuxBool() : Option[Bool] = muxBool

  override def toString() : String = {
    "Node@" + hashCode + "(" + letter() + ") { " + uk + " } { " + ck + " }"
  }

  private var _hashIdx = -1
  def hashIdx = _hashIdx
  def hashIdx_= ( value : Int ) : Unit = {
    if ( _hashIdx == -1 )
      _hashIdx = value
  }


}
