/** This file implements valid transformations on
  * a different numbers of Nodes
  */
package chiselutils.algorithms

import collection.mutable.ArrayBuffer
import scala.util.Random

object Transforms {

  /** Combine two uks into 1 and return the index mapping to them
    */
  def commonMapping( uKa : Seq[Set[Seq[Int]]], uKb : Seq[Set[Seq[Int]]]) :
      ( Seq[Set[Seq[Int]]], Seq[Int], Seq[Int] ) = {
    var aIdx = 0
    var bIdx = 0
    val aIdxMap = ArrayBuffer[Int]()
    val bIdxMap = ArrayBuffer[Int]()
    val uKc = ArrayBuffer[Set[Seq[Int]]]()
    while ( aIdx < uKa.size || bIdx < uKb.size ) {
      if ( bIdx == uKb.size || ( aIdx < uKa.size && uKa(aIdx).hashCode < uKb(bIdx).hashCode ) ) {
        aIdxMap += uKc.size
        uKc += uKa( aIdx )
        aIdx += 1
      } else {
        bIdxMap += uKc.size
        if ( aIdx < uKa.size && uKa( aIdx ) == uKb( bIdx ) ) {
          aIdx += 1
          aIdxMap += uKc.size 
        }
        uKc += uKb( bIdx )
        bIdx += 1
      }
    }
    ( uKc.toVector, aIdxMap.toVector, bIdxMap.toVector )
  }

  /** Look at two nodes and try to merge them
    * return new node if merge was done
    * seach parents for common l/r
    * check overlap and that constraint type the same
    * create new merged node
    * look if all L or all R for mux. if so set l/r
    */
  def tryMerge( nA : Node , nB : Node ) : Option[Node] = {

    // checkType
    if ( nA.isA() != nB.isA() || nA.isB() != nB.isB() || nA.isC() != nB.isC() )
      return None

    // check number of distinct inputs
    val sameIn = nA.getL() == nB.getL() && nA.getR() == nB.getR()
    val oppositeIn = nA.getL() == nB.getR() && nA.getR() == nB.getL()
    if ( sameIn || oppositeIn ) {
      val mapping = commonMapping( nA.uk, nB.uk )
      val ck = ( nA.ck zip nB.ck ).map( cki => {
        if ( cki._1 == -1 && cki._2 == -1 )
          -1
        else if ( cki._2 == -1 )
          mapping._2( cki._1 )
        else if ( cki._1 == -1 )
          mapping._3( cki._2 )
        else {
          if ( mapping._2( cki._1 ) != mapping._3( cki._2 ) )
            return None // as cannot merge under this condition
          mapping._2( cki._1 )
        }
      })
      // create the new node
      val nC = Node( mapping._1, ck )
      if ( nA.isA() )
        nC.setA()
      if ( nA.isB() )
        nC.setB()
      if ( nA.isC() )
        nC.setC()

      // set l/r
      nC.setL( nA.getL() )
      nC.setR( nA.getR() )

      // fix parent links
      for ( p <- nA.getParents() ++ nB.getParents() ) {
        if ( p.getL().isDefined && ( p.getL().get == nA || p.getL().get == nB ) )
          p.setL( Some(nC) )
        if ( p.getR().isDefined && ( p.getR().get == nA || p.getR().get == nB ) )
          p.setR( Some(nC) )
      }

      return Some( nC )
    }

    None
  }

  /** Increment the integers in position 1 of vectors
    */
  private def incr( uk : Seq[Set[Seq[Int]]] ) : Seq[Set[Seq[Int]]] = {
    uk.map( s => s.map( v => { List( v(0) + 1 ) ++ v.drop(1) }.to[Seq]))
  }

  /** Combine two uk/cks in an add
    */
  private def combineAdd( uk1 : Seq[Set[Seq[Int]]], ck1 : Seq[Int],
    uk2 : Seq[Set[Seq[Int]]], ck2 : Seq[Int] ) : ( Seq[Set[Seq[Int]]], Seq[Int] ) = {
    val ckCombined = ck1.zip( ck2 )
    val uKidx = ckCombined.distinct.filter( _ != ( -1, -1 ) )
    val ckNew = ckCombined.map( uKidx.indexOf( _ ) )
    // should never have one as -1 and not the other
    val ukNew = uKidx.map( uki => uk1( uki._1 ) ++ uk2( uki._2 ) )
    ( ukNew, ckNew )
  }

  /** Combine two uk/cks in a mux
    */
  private def combineMux( uk1 : Seq[Set[Seq[Int]]], ck1 : Seq[Int],
    uk2 : Seq[Set[Seq[Int]]], ck2 : Seq[Int] ) : ( Seq[Set[Seq[Int]]], Seq[Int] ) = {
    val ukNew = (uk1 ++ uk2).distinct
    val uk1Idx = uk1.map( uki => ukNew.indexOf(uki) )
    val uk2Idx = uk2.map( uki => ukNew.indexOf(uki) )
    val ckNew = ck1.zip( ck2 ).map( cks => {
      if ( cks._1 == -1 && cks._2 == -1 )
        -1
      else if ( cks._1 == -1 )
        uk2Idx( cks._2 )
      else if ( cks._2 == -1 )
        uk1Idx( cks._1 )
      else {
        assert( uk1Idx( cks._1 ) == uk2Idx( cks._2 ), "Invalid mux combine of {" + uk1 + ", " + ck1 + "} and {" + uk2 + ", " + ck2 + "}" )
        uk1Idx( cks._1 )
      }
    })
    ( ukNew, ckNew )
  }

  /** pass ck through the ckFilter so that cks with other parents aren't included
    */
  private def filterCk( ck : Seq[Int], uk : Seq[Set[Seq[Int]]], ckFilter : Seq[Int], ukFilter : Seq[Set[Seq[Int]]] ) : Seq[Int] = {
    val mapping = ukFilter.map( uki => if ( uki != -1 ) uk.indexOf( uki ) else -1 )
    ckFilter.map( cki => if ( cki != -1 ) mapping( cki ) else -1 ).zip( ck ).map( cks => if ( cks._1 == -1 ) -1 else cks._2 )
  }

  /** ignore uks, useful for adds with no other parents
    */
  private def filterCk( ck : Seq[Int], ckFilter : Seq[Int] ) : Seq[Int] = {
    ckFilter.zip( ck ).map( cks => if ( cks._1 == -1 ) -1 else cks._2 )
  }


  /** nSwap satisfies constraintA, nPar satisfies constraintB
    * nPar.lNode = nSwap
    * nPar.rNode = nSwap
    * transformed to (6):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeB
    * nodeA.lNode = nSwap.lNode
    * nodeA.rNode = nSwap.lNode
    * nodeB.lNode = nSwap.rNode
    * nodeB.rNode = nSwap.rNode
    */
  private def swapCase1( nPar : Node, nSwap : Node ) : Seq[Node] = {
    val nodeAuK = nSwap.getL().get.getUkNext()
    val nodeAcKFiltered = filterCk( nSwap.getL().get.getCkNext(), nSwap.ck )

    val nodeA = Node( nodeAuK, nodeAcKFiltered )
    nodeA.setL( nSwap.getL() )
    nodeA.setR( nSwap.getL() )
    nodeA.setB()
    val nodeBuK = nSwap.getR().get.getUkNext()
    val nodeBcKFiltered = filterCk( nSwap.getR().get.getCkNext(), nSwap.ck )

    val nodeB = Node( nodeBuK, nodeBcKFiltered )
    nodeB.setL( nSwap.getR() )
    nodeB.setR( nSwap.getR() )
    nodeB.setB()

    nPar.setL( Some(nodeA) )
    nPar.setR( Some(nodeB) )
    nPar.setA()
    List( nPar, nodeA, nodeB )
  }

  /** nSwap satisfies constraintB, nPar satisfies constraintB
    * nPar.lNode = nSwap
    * nPar.rNode = nSwap
    * transformed to (10):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeB
    * nodeA.lNode = nSwap.lNode
    * nodeA.rNode = nSwap.lNode
    * nodeB.lNode = nSwap.rNode
    * nodeB.rNode = nSwap.rNode
    */
  private def swapCase2( nPar : Node, nSwap : Node ) : Seq[Node] = {
    val nodeAuK = nSwap.getL().get.getUkNext()
    val nodeAcKFiltered = filterCk( nSwap.getL().get.getCkNext(), nodeAuK, nSwap.ck, nSwap.uk )

    val nodeA = Node( nodeAuK, nodeAcKFiltered )
    nodeA.setL( nSwap.getL() )
    nodeA.setR( nSwap.getL() )
    nodeA.setB()
    val nodeBuK = nSwap.getR().get.getUkNext()
    val nodeBcKFiltered = filterCk( nSwap.getR().get.getCkNext(), nodeBuK, nSwap.ck, nSwap.uk )

    val nodeB = Node( nodeBuK, nodeBcKFiltered )
    nodeB.setL( nSwap.getR() )
    nodeB.setR( nSwap.getR() )
    nodeB.setB()

    nPar.setL( Some(nodeA) )
    nPar.setR( Some(nodeB) )
    nPar.setB()
    List( nPar, nodeA, nodeB )
  }

  /** nSwap satisfies constraintB, nPar satisfies constraintA, nOther satisfies constraintA
    * nPar.lNode = nSwap
    * nPar.rNode = nOther
    * nSwap.lNode = nSwap.rNode
    * transformed to (10):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeB
    * nodeA.lNode = nSwap.lNode
    * nodeA.rNode = nOther.lNode
    * nodeB.lNode = nOther.rNode
    * nodeB.rNode = nOther.rNode
    */
  private def swapCase4( nPar : Node, nSwap : Node, nOther : Node ) : Seq[Node] = {

    // nSwap must be the reg by itself
    // rotate so that nOther.rNode + nSwap.lNode and nOther.lNode is reg

    val otherLuK = nOther.getL().get.getUkNext()
    val otherLcK = filterCk( nOther.getL().get.getCkNext(), nOther.ck )

    val nodeA = Node( otherLuK, otherLcK )
    nodeA.setL( nOther.getL() )
    nodeA.setR( nOther.getL() )
    nodeA.setB()

    // find distinct ck combinations
    val otherRuK = nOther.getR().get.getUkNext()
    val otherRcK = filterCk( nOther.getR().get.getCkNext(), nOther.ck )
    val swapuK = nSwap.uk
    val swapcK = nSwap.ck

    // combine two as union
    val combAdd = combineAdd( swapuK, swapcK, otherRuK, otherRcK )
    val nodeB = Node( combAdd._1, combAdd._2 )
    nodeB.setA()
    nodeB.setL( nOther.getR() )
    nodeB.setR( nSwap.getL() )

    nPar.setL( Some(nodeA) )
    nPar.setR( Some(nodeB) )
    nPar.setA()
    List( nPar, nodeA, nodeB )
  }

  /** nSwap satisfies constraintA, nPar satisfies constraintA, nOther satisfies constraintA
    * nPar.lNode = nSwap
    * nPar.rNode = nOther
    * transformed to (5):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeB
    * nodeA.lNode = nOther.lNode
    * nodeA.rNode = nSwap.lNode
    * nodeB.lNode = nSwap.rNode
    * nodeB.rNode = nOther.rNode
    */
  private def swapCase5( nPar : Node, nSwap : Node, nOther : Node ) : Seq[Node] = {
    val otherLFiltered = filterCk( nOther.getL().get.getCkNext(), nOther.ck )
    val otherRFiltered = filterCk( nOther.getR().get.getCkNext(), nOther.ck )
    val swapLFiltered = filterCk( nSwap.getL().get.getCkNext(), nSwap.ck )
    val swapRFiltered = filterCk( nSwap.getR().get.getCkNext(), nSwap.ck )
    val nodeAComb = combineAdd( nOther.getL().get.getUkNext(), otherLFiltered,
      nSwap.getL().get.getUkNext(), swapLFiltered )
    val nodeBComb = combineAdd( nOther.getR().get.getUkNext(), otherRFiltered,
      nSwap.getR().get.getUkNext(), swapRFiltered )

    val nodeA = Node( nodeAComb._1, nodeAComb._2 )
    val nodeB = Node( nodeBComb._1, nodeBComb._2 )
    nodeA.setA()
    nodeA.setL( nOther.getL() )
    nodeA.setR( nSwap.getL() )
    nodeB.setL( nSwap.getR() )
    nodeB.setR( nOther.getR() )
    nodeB.setA()

    nPar.setL( Some(nodeA) )
    nPar.setR( Some(nodeB) )
    nPar.setA()
    List( nPar, nodeA, nodeB )
  }

  /** nSwap satisfies constraintB, nPar satisfies constraintA, nOther satisfies constraintB
    * nPar.lNode = nSwap
    * nPar.rNode = nOther
    * nSwap.lNode = nSwap.rNode
    * nOther.lNode = nOther.rNode
    * transformed to (1):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeA
    * nodeA.lNode = nSwap.lNode
    * nodeA.rNode = nOther.lNode
    */
  private def swapCase6( nPar : Node, nSwap : Node, nOther : Node ) : Seq[Node] = {

    val othercK = filterCk( nOther.getL().get.getCkNext(), nOther.getL().get.getUkNext(), nOther.ck, nOther.uk )
    val otheruK = nOther.getL().get.getUkNext()
    val swapcK = filterCk( nSwap.getL().get.getCkNext(), nSwap.getL().get.getUkNext(), nSwap.ck, nSwap.uk )
    val swapuK = nSwap.getL().get.getUkNext()
    val combAdd = combineAdd( otheruK, othercK, swapuK, swapcK )
    val nodeA = Node( combAdd._1, combAdd._2 )
    nodeA.setA()
    nodeA.setL( nSwap.getL() )
    nodeA.setR( nOther.getL() )

    nPar.setL( Some(nodeA) )
    nPar.setR( Some(nodeA) )
    nPar.setB()
    List( nPar, nodeA )
  }

  /** nSwap satisfies constraintB, nPar satisfies constraintA, nOther satisfies constraintB
    * nPar.lNode = nSwap
    * nPar.rNode = nOther
    * nSwap.lNode = nSwap.rNode
    * transformed to (11):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeB
    * nodeA.lNode = nSwap.lNode
    * nodeA.rNode = nOther.lNode
    * nodeB.lNode = nSwap.lNode
    * nodeB.rNode = nOther.rNode
    */
  private def swapCase7( nPar : Node, nSwap : Node, nOther : Node ) : Seq[Node] = {

    val otherLcK = filterCk( nOther.getL().get.getCkNext(), nOther.getL().get.getUkNext(), nOther.ck, nOther.uk )
    val otherLuK = nOther.getL().get.getUkNext()
    val otherRcK = filterCk( nOther.getR().get.getCkNext(), nOther.getR().get.getUkNext(), nOther.ck, nOther.uk )
    val otherRuK = nOther.getR().get.getUkNext()
    val swapcKL = filterCk( nSwap.ck, otherLcK )
    val swapcKR = filterCk( nSwap.ck, otherRcK )
    val combAddL = combineAdd( otherLuK, otherLcK, nSwap.uk, swapcKL )
    val combAddR = combineAdd( otherRuK, otherRcK, nSwap.uk, swapcKR )
    val nodeA = Node( combAddL._1, combAddL._2 )
    nodeA.setL( nOther.getL() )
    nodeA.setR( nSwap.getL() )
    nodeA.setA()
    val nodeB = Node( combAddR._1, combAddR._2 )
    nodeB.setL( nSwap.getL() )
    nodeB.setR( nOther.getR() )
    nodeB.setA()
    nPar.setL( Some(nodeA) )
    nPar.setR( Some(nodeB) )
    nPar.setB()
    List( nPar, nodeA, nodeB )
  }

  /** nSwap satisfies constraintB, nPar satisfies constraintA, nOther satisfies constraintB
    * nPar.lNode = nSwap
    * nPar.rNode = nOther
    * nSwap.lNode = nSwap.rNode
    * nOther.lNode = nOther.rNode
    * transformed to (2):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeA
    * nodeA.lNode = nSwap.lNode
    * nodeA.rNode = nOther.lNode
    */
  private def swapCase10( nPar : Node, nSwap : Node, nOther : Node ) : Seq[Node] = {

    val othercK = filterCk( nOther.getL().get.getCkNext(), nOther.getL().get.getUkNext(), nOther.ck, nOther.uk )
    val otheruK = nOther.getL().get.getUkNext()
    val swapcK = filterCk( nSwap.getL().get.getCkNext(), nSwap.getL().get.getUkNext(), nSwap.ck, nSwap.uk )
    val swapuK = nSwap.getL().get.getUkNext()
    val combMux = combineMux( otheruK, othercK, swapuK, swapcK )
    val nodeA = Node( combMux._1, combMux._2 )
    nodeA.setL( nSwap.getL() )
    nodeA.setR( nOther.getL() )
    nodeA.setB()
    nPar.setL( Some(nodeA) )
    nPar.setR( Some(nodeA) )
    nPar.setB()
    List( nPar, nodeA )
  }

  /** nSwap satisfies constraintA, nPar satisfies constraintB, nOther satisfies constraintA
    * nPar.lNode = nSwap
    * nPar.rNode = nOther
    * iff nSwap and nOther have lNode or rNode in common
    * transformed to (7):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeB
    * nodeA.lNode = nSwap.lNode
    * nodeA.rNode = nSwap.lNode
    * nodeB.lNode = nOther.lNode
    * nodeB.lNode = nOther.rNode
    */
  private def swapCase11( nPar : Node, nSwap : Node, nOther : Node ) : Seq[Node] = {

    // find common
    val swapLOtherL = nSwap.getL().get == nOther.getL().get
    val swapLOtherR = nSwap.getL().get == nOther.getR().get
    val swapROtherL = nSwap.getR().get == nOther.getL().get
    val swapROtherR = nSwap.getR().get == nOther.getR().get
    val swapL = swapLOtherL || swapLOtherR
    val swapR = swapROtherL || swapROtherR
    val otherL = swapLOtherL || swapROtherL
    if ( swapL || swapR ) {
      val commonNode = { if ( swapL ) nSwap.getL().get else nSwap.getR().get }
      val swapSpare = { if ( swapL ) nSwap.getR().get else nSwap.getL().get }
      val otherSpare = { if ( otherL ) nOther.getR().get else nOther.getL().get }

      val nodeAuK = commonNode.getUkNext()
      val nodeAcKUp = commonNode.getCkNext()
      val ckComb = nSwap.ck.zip( nOther.ck ).map( cks => {
        if ( cks._1 == -1 )
          cks._2
        else
          cks._1
      })
      val nodeAcK = filterCk( nodeAcKUp, ckComb )
      val nodeA = Node( nodeAuK, nodeAcK )
      nodeA.setB()
      nodeA.setL( Some(commonNode) )
      nodeA.setR( Some(commonNode) )

      val swapCkFiltered = filterCk( swapSpare.getCkNext(), nSwap.ck )
      val otherCkFiltered = filterCk( otherSpare.getCkNext(), nOther.ck )
      val combMux = combineMux( swapSpare.getUkNext(), swapCkFiltered,
        otherSpare.getUkNext(), otherCkFiltered )
      val nodeB = Node( combMux._1, combMux._2 )
      nodeB.setB()
      nodeB.setL( Some(swapSpare) )
      nodeB.setR( Some(otherSpare) )

      nPar.setL( Some(nodeA) )
      nPar.setR( Some(nodeB) )
      nPar.setA()
      return List( nPar, nodeA, nodeB )
    }
    List[Node]()
  }

  /** nSwap satisfies constraintB, nPar satisfies constraintB, nOther satisfies constraintB
    * nPar.lNode = nSwap
    * nPar.rNode = nOther
    * transformed to (12):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeB
    * nodeA.lNode = nOther.lNode
    * nodeA.rNode = nSwap.lNode
    * nodeB.lNode = nSwap.rNode
    * nodeB.rNode = nOther.rNode
    */
  private def swapCase12( nPar : Node, nSwap : Node, nOther : Node ) : Seq[Node] = {

    val otherLFiltered = filterCk( nOther.getL().get.getCkNext(), nOther.getL().get.getUkNext(), nOther.ck, nOther.uk )
    val otherRFiltered = filterCk( nOther.getR().get.getCkNext(), nOther.getR().get.getUkNext(), nOther.ck, nOther.uk )
    val swapLFiltered = filterCk( nSwap.getL().get.getCkNext(), nSwap.getL().get.getUkNext(), nSwap.ck, nSwap.uk )
    val swapRFiltered = filterCk( nSwap.getR().get.getCkNext(), nSwap.getR().get.getUkNext(), nSwap.ck, nSwap.uk )
    val nodeAComb = combineMux( nOther.getL().get.getUkNext(), otherLFiltered,
      nSwap.getL().get.getUkNext(), swapLFiltered )
    val nodeBComb = combineMux( nOther.getR().get.getUkNext(), otherRFiltered,
      nSwap.getR().get.getUkNext(), swapRFiltered )

    val nodeA = Node( nodeAComb._1, nodeAComb._2 )
    val nodeB = Node( nodeBComb._1, nodeBComb._2 )
    nodeA.setB()
    nodeA.setL( nOther.getL() )
    nodeA.setR( nSwap.getL() )
    nodeB.setL( nSwap.getR() )
    nodeB.setR( nOther.getR() )
    nodeB.setB()
    nPar.setL( Some(nodeA) )
    nPar.setR( Some(nodeB) )
    nPar.setB()
    List( nPar, nodeA, nodeB )
  }

  /** nSwap satisfies constraintB, nPar satisfies constraintB, nOther satisfies constraintB
    * nPar.lNode = nSwap
    * nPar.rNode = nOther
    * transformed to (13):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeB
    * nodeA.lNode = nOther.lNode
    * nodeA.rNode = nOther.lNode
    * nodeB.lNode = nOther.rNode
    * nodeB.rNode = nSwap.lNode
    */
  private def swapCase13( nPar : Node, nSwap : Node, nOther : Node ) : Seq[Node] = {

    val otherLuK = nOther.getL().get.getUkNext()
    val otherLcK = filterCk( nOther.getL().get.getCkNext(), otherLuK, nOther.ck, nOther.uk )
    val nodeA = Node( otherLuK, otherLcK )
    nodeA.setL( nOther.getL() )
    nodeA.setR( nOther.getL() )
    nodeA.setB()
    // find distinct ck combinations
    val otherRuK = nOther.getR().get.getUkNext()
    val otherRcK = filterCk( nOther.getR().get.getCkNext(), otherRuK, nOther.ck, nOther.uk )
    val swapuK = nSwap.uk
    val swapcK = nSwap.ck

    // combine two as union
    val combMux = combineMux( swapuK, swapcK, otherRuK, otherRcK )
    val nodeB = Node( combMux._1, combMux._2 )
    nodeB.setB()
    nodeB.setL( nOther.getR() )
    nodeB.setR( nSwap.getL() )

    nPar.setL( Some(nodeA) )
    nPar.setR( Some(nodeB) )
    nPar.setB()
    List( nPar, nodeA, nodeB )
  }

  /** Look at two nodes and try to swap them
    */
  def trySwap( nPar : Node, nSwap : Node, applyIfIncrease : Boolean = true ) : (Seq[Node], Int) = {

    assert( nSwap.hasParent( nPar ), "For swap must have swap and parent node" )

    if ( nSwap.isC() )
      return (List[Node](), 0)

    // work out how nodes are connected ( should be directly )
    if ( nPar.isB() && nPar.getL() == nPar.getR() ) {
      if ( nSwap.isA() )
        return { if ( applyIfIncrease ) ( swapCase1( nPar, nSwap ), 1 ) else ( List[Node](), 0 ) }
      if ( nSwap.isB() && nSwap.getL() != nSwap.getR() )
        return { if ( applyIfIncrease ) ( swapCase2( nPar, nSwap ), 2 ) else ( List[Node](), 0) }
      return (List[Node](), 0) // case 3 which no point as changes nothing
    }
    val nOther = { if ( nPar.getL().get == nSwap ) nPar.getR().get else nPar.getL().get }

    if ( nPar.isA() ) {
      if ( nSwap.isB() && nSwap.getL() == nSwap.getR() ) {
        if ( nOther.isA() )
          return ( swapCase4( nPar, nSwap, nOther ), 4 )
        if ( nOther.isB() && nOther.getL() == nOther.getR() )
          return ( swapCase6( nPar, nSwap, nOther ), 6 )
        if ( nOther.isB() )
          return ( swapCase7( nPar, nSwap, nOther ), 7 )
      }

      if ( nOther.isB() && nOther.getL() == nOther.getR() ) {
        if ( nSwap.isA() )
          return ( swapCase4( nPar, nOther, nSwap ), 4 )
        if ( nSwap.isB() )
          return ( swapCase7( nPar, nOther, nSwap ), 7 )
      }

      if ( nSwap.isA() && nOther.isA() )
        return ( swapCase5( nPar, nSwap, nOther ), 5 )
    }

    if ( nPar.isB() ) {
      if ( nSwap.isB() && nSwap.getL() == nSwap.getR() ) {
        if ( nOther.isB() && nOther.getL() == nOther.getR() )
          return ( swapCase10( nPar, nSwap, nOther ), 10 )
        if ( nOther.isB() )
          return ( swapCase13( nPar, nSwap, nOther ), 13 )
        return ( List[Node](), 0 )
      }

      if ( nOther.isB() && nOther.getL() == nOther.getR() ) {
        if ( nSwap.isB() )
          return ( swapCase13( nPar, nOther, nSwap ), 13 )
      }

      if ( nSwap.isB() && nOther.isB() )
        return ( swapCase12( nPar, nSwap, nOther ), 12 )

      if ( nSwap.isA() && nOther.isA() )
        return ( swapCase11( nPar, nSwap, nOther ), 11 )
    }
    ( List[Node](), 0 )
  }

  /** Split a node and randomally assign its parents to each
    */
  def trySplit( nA : Node ) : Seq[Node] = {
    if ( nA.getParents().size <= 1 || nA.isC() )
      return List[Node]()

    val shuffledPar = Random.shuffle( nA.getParents() )
    val splitIdx = Random.nextInt( shuffledPar.size - 1 )

    val ckNeeded = shuffledPar.map( n => {
      if ( n.isA() || n.getL() == n.getR() ) {
        // adds or reg are easy as all are needed
        n.getCkPrev().zip( nA.ck ).map( ck => {
          if ( ck._1 == -1 )
            -1
          else
            ck._2
        })
      } else {
        // mux
        val prevCk = n.getCkPrev()
        val prevUk = n.getUkPrev()
        // determine for each ck if uk is provided by split
        prevCk.zip( nA.ck ).map( ck => {
          if ( ck._1 == -1 )
            -1
          else {
            if ( ck._2 != -1 && prevUk( ck._1 ) == nA.uk( ck._2 ) )
              ck._2
            else
              -1
          }
        })
      }
    })

    val ( n1Par, n2Par ) = shuffledPar.splitAt( splitIdx + 1 )
    val ( ck1, ck2 ) = ckNeeded.splitAt( splitIdx + 1 )
    val n1Ck = ( 0 until nA.nodeSize ).map( idx => ck1.map( cks => cks( idx ) ).reduce( (x,y) => {
      if ( x == -1 )
        y
      else {
        assert( y == x || y == -1, "Invalid combining of parents" )
        x
      }
    })).toVector
    val n2Ck = ( 0 until nA.nodeSize ).map( idx => ck2.map( cks => cks( idx ) ).reduce( (x,y) => {
      if ( x == -1 )
        y
      else {
        assert( y == x || y == -1, "Invalid combining of parents" )
        x
      }
    })).toVector
    val n1 = Node( nA.uk, n1Ck )
    val n2 = Node( nA.uk, n2Ck )
    if ( nA.getL().isDefined ) {
      n1.setL( nA.getL() )
      n2.setL( nA.getL() )
    }
    if ( nA.getR().isDefined ) {
      n1.setR( nA.getR() )
      n2.setR( nA.getR() )
    }
    if ( nA.isA() ) {
      n1.setA()
      n2.setA()
    }
    if ( nA.isB() ) {
      n1.setB()
      n2.setB()
    }
    for ( p <- n1Par ) {
      if ( p.getL().isDefined && p.getL().get == nA )
        p.setL( Some(n1) )
      if ( p.getR().isDefined && p.getR().get == nA )
        p.setR( Some(n1) )
    }
    for ( p <- n2Par ) {
      if ( p.getL().isDefined && p.getL().get == nA )
        p.setL( Some(n2) )
      if ( p.getR().isDefined && p.getR().get == nA )
        p.setR( Some(n2) )
    }

    return List( n1, n2 )
  }

}
