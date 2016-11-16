import org.junit.Assert._
import org.junit.Test
import org.junit.Ignore
import scala.util.Random
import scala.collection.mutable.ArrayBuffer
import chiselutils.algorithms.Node
import chiselutils.algorithms.Transforms

class SumScheduleSuite extends TestSuite {

  val myRand = new Random
  val dim = 2
  val nodeSize = 20

  def decr( uk : List[Set[Vector[Int]]] ) : List[Set[Vector[Int]]] = {
    uk.map( s => s.map( v => { Vector( v(0) - 1 ) ++ v.drop(1) }))
  }

  def incr( uk : List[Set[Vector[Int]]] ) : List[Set[Vector[Int]]] = {
    uk.map( s => s.map( v => { Vector( v(0) + 1 ) ++ v.drop(1) }))
  }


  /** Test the sum constraint
    */
  @Test def testConstraintA {
    val noUk = 5
    val setSize = 5
    val maxVal = 10
    val nodeAuK = List.fill( noUk ) { List.fill( setSize ) { Vector.fill( dim ) { myRand.nextInt( maxVal ) + 1 }}.toSet }
    val nodeAcK = List.fill( nodeSize ) { myRand.nextInt( noUk ) }
    val nodeA = Node( nodeAuK, nodeAcK )
    val nodeBCuK = nodeAuK.map( uki => {
      val listB = ArrayBuffer[Vector[Int]]()
      val listC = ArrayBuffer[Vector[Int]]()
      for ( s <- uki ) {
        if ( listB.isEmpty || ( myRand.nextInt( 2 ) == 0 && !listC.isEmpty )  )
          listB += s
        else
          listC += s
      }
      ( listB.toSet, listC.toSet )
    })
    val nodeB = Node( decr(nodeBCuK.map( _._1 )), nodeAcK.drop(1) ++ nodeAcK.take(1) )
    val nodeC = Node( decr(nodeBCuK.map( _._2 )), nodeAcK.drop(1) ++ nodeAcK.take(1) )
    nodeA.setL( nodeB )
    nodeA.setR( nodeC )
    assert( Node.satisfiesConstraintA( nodeA ) )
  }

  /** Test the mux constraint
    * NB: this test will fail if you get unlucky and all mux on oneside (other is empty)
    */
  @Test def testConstraintB {
    val noUk = 5
    val setSize = 5
    val maxVal = 10
    val nodeAuK = List.fill( noUk ) { List.fill( setSize ) { Vector.fill( dim ) { myRand.nextInt( maxVal ) + 1 }}.toSet }
    val nodeAcK = List.fill( nodeSize ) { myRand.nextInt( noUk ) }
    val nodeA = Node( nodeAuK, nodeAcK )
    val muxSwitch = nodeAuK.map( uki => myRand.nextInt( 2 ) )
    val shiftCk = nodeAcK.drop(1) ++ nodeAcK.take(1)
    val lIdxMap = muxSwitch.scanLeft(0)( _ + _ )
    val rIdxMap = muxSwitch.map( 1 - _ ).scanLeft(0)( _ + _ )
    val lCk = shiftCk.map( cki => {
      if ( cki == -1 || muxSwitch( cki ) == 0 )
        -1
      else
        lIdxMap( cki )
    })
    val rCk = shiftCk.map( cki => {
      if ( cki == -1 || muxSwitch( cki ) == 1 )
        -1
      else
        rIdxMap( cki )
    })

    val nodeBuK = decr(nodeAuK.zip( muxSwitch ).filter( _._2 == 1 ).map( _._1 ))
    val nodeCuK = decr(nodeAuK.zip( muxSwitch ).filter( _._2 == 0 ).map( _._1 ))
    val nodeB = {
      if ( nodeBuK.size != 0 )
        Node( nodeBuK, lCk )
      else
        Node( nodeCuK, rCk )
    }
    val nodeC = {
      if ( nodeBuK.size == 0 || nodeCuK.size == 0 )
        nodeB
      else
        Node( nodeCuK, rCk )
    }
    nodeA.setL( nodeB )
    nodeA.setR( nodeC )
    assert( Node.satisfiesConstraintB( nodeA ) )
  }

  @Test def testConstraintC {
    val nodeAuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 1 } ))
    val nodeAcK = List.fill( nodeSize ) { myRand.nextInt( 2 ) - 1 }
    val nodeA = Node( nodeAuK, nodeAcK )
    assert( Node.satisfiesConstraintC( nodeA ) )
  }

  @Test def testSwap1 {
    val nodeAuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 0 } )) // termination setA
    val nodeBuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 1 } )) // termination setB
    val node_cK = List.fill( nodeSize ) { myRand.nextInt( 2 ) - 1 }
    val nodeA = Node( nodeAuK, node_cK )
    nodeA.setC()
    val nodeB = Node( nodeBuK, node_cK )
    nodeB.setC()
    val nodeSwapUk = incr( nodeAuK.zip( nodeBuK ).map( z => z._1 ++ z._2 ) )
    val nodeSwapCk = node_cK.takeRight( 1 ) ++ node_cK.dropRight( 1 )
    val nodeSwap = Node( nodeSwapUk, nodeSwapCk )
    nodeSwap.setA()
    nodeSwap.setL( nodeA )
    nodeSwap.setR( nodeB )
    val nodePar = Node( nodeSwap.getUkNext(), nodeSwap.getCkNext() )
    nodePar.setB()
    nodePar.setL( nodeSwap )
    nodePar.setR( nodeSwap )
    assert( Node.satisfiesConstraintC( nodeA ) )
    assert( Node.satisfiesConstraintC( nodeB ) )
    assert( Node.satisfiesConstraintA( nodeSwap ) )
    assert( Node.satisfiesConstraintB( nodePar ) )

    val nodeList = Transforms.trySwap( nodePar, nodeSwap )

    assert( Node.satisfiesConstraintA( nodeList(0) ) && nodeList(0).isA() )
    assert( Node.satisfiesConstraintB( nodeList(1) ) && nodeList(1).isB() )
    assert( Node.satisfiesConstraintB( nodeList(2) ) && nodeList(2).isB() )
  }

  @Test def testSwap2 {
    val nodeAuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 0 } )) // termination setA
    val nodeBuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 1 } )) // termination setB
    val node_cK = List.fill( nodeSize ) { myRand.nextInt( 2 ) }
    val nodeA = Node( nodeAuK, node_cK.map( -_ ) )
    nodeA.setC()
    val nodeB = Node( nodeBuK, node_cK.map( _ - 1 ) )
    nodeB.setC()
    val nodeSwapUk = incr( nodeAuK ++ nodeBuK )
    val nodeSwapCk = node_cK.takeRight( 1 ) ++ node_cK.dropRight( 1 )
    val nodeSwap = Node( nodeSwapUk, nodeSwapCk )
    nodeSwap.setB()
    nodeSwap.setL( nodeA )
    nodeSwap.setR( nodeB )
    val nodeParUk = incr( nodeSwapUk )
    val nodeParCk = nodeSwapCk.takeRight( 1 ) ++ nodeSwapCk.dropRight( 1 )
    val nodePar = Node( nodeParUk, nodeParCk )
    nodePar.setB()
    nodePar.setL( nodeSwap )
    nodePar.setR( nodeSwap )
    assert( Node.satisfiesConstraintC( nodeA ) )
    assert( Node.satisfiesConstraintC( nodeB ) )
    assert( Node.satisfiesConstraintB( nodeSwap ) )
    assert( Node.satisfiesConstraintB( nodePar ) )

    val nodeList = Transforms.trySwap( nodePar, nodeSwap )

    assert( Node.satisfiesConstraintB( nodeList(0) ) && nodeList(0).isB() )
    assert( Node.satisfiesConstraintB( nodeList(1) ) && nodeList(1).isB() )
    assert( Node.satisfiesConstraintB( nodeList(2) ) && nodeList(2).isB() )
  }

  @Test def testSwap4 {
    val nodeAuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 0 } )) // termination setA
    val nodeBuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 1 } )) // termination setB
    val nodeCuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 2 } )) // termination setC
    val node_cK = List.fill( nodeSize ) { myRand.nextInt( 2 ) - 1 }
    val nodeA = Node( nodeAuK, node_cK )
    val nodeB = Node( nodeBuK, node_cK )
    val nodeC = Node( nodeCuK, node_cK )
    nodeA.setC()
    nodeB.setC()
    nodeC.setC()
    val nodeSwap = Node( nodeA.getUkNext(), nodeA.getCkNext() )
    nodeSwap.setB()
    val nodeOtherUk = incr( nodeBuK.zip( nodeCuK ).map( z => z._1 ++ z._2 ) )
    val nodeOtherCk = nodeB.getCkNext()
    val nodeOther = Node( nodeOtherUk, nodeOtherCk )
    nodeOther.setA()

    nodeSwap.setL( nodeA )
    nodeSwap.setR( nodeA )
    nodeOther.setL( nodeB )
    nodeOther.setR( nodeC )

    val nodeParUk = incr( nodeSwap.getUk().zip( nodeOther.getUk() ).map( z => z._1 ++ z._2 ) )
    val nodeParCk = nodeSwap.getCkNext()
    val nodePar = Node( nodeParUk, nodeParCk )
    nodePar.setA()
    nodePar.setL( nodeSwap )
    nodePar.setR( nodeOther )

    assert( Node.satisfiesConstraintC( nodeA ) )
    assert( Node.satisfiesConstraintC( nodeB ) )
    assert( Node.satisfiesConstraintC( nodeC ) )
    assert( Node.satisfiesConstraintB( nodeSwap ) )
    assert( Node.satisfiesConstraintA( nodeOther ) )
    assert( Node.satisfiesConstraintA( nodePar ) )

    val nodeList = Transforms.trySwap( nodePar, nodeSwap )

    assert( Node.satisfiesConstraintA( nodeList(0) ) && nodeList(0).isA() )
    assert( Node.satisfiesConstraintB( nodeList(1) ) && nodeList(1).isB() )
    assert( Node.satisfiesConstraintA( nodeList(2) ) && nodeList(2).isA() )
  }

  @Test def testSwap5 {
    val nodeAuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 0 } )) // termination setA
    val nodeBuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 1 } )) // termination setB
    val nodeCuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 2 } )) // termination setC
    val nodeDuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 3 } )) // termination setC
    val nodeParCk = List.fill( nodeSize ) { myRand.nextInt(2) - 1 }
    val node_cK = nodeParCk.drop(2) ++ nodeParCk.take(2)
    val node_cK1 = nodeParCk.drop(1) ++ nodeParCk.take(1)
    val nodeA = Node( nodeAuK, node_cK )
    val nodeB = Node( nodeBuK, node_cK )
    val nodeC = Node( nodeCuK, node_cK )
    val nodeD = Node( nodeDuK, node_cK )
    nodeA.setC()
    nodeB.setC()
    nodeC.setC()
    nodeD.setC()
    val nodeSwap = Node( nodeA.getUkNext().map( z => nodeB.getUkNext().head ++ z ), node_cK1 )
    val nodeOther = Node( nodeC.getUkNext().map( z => nodeD.getUkNext().head ++ z ), node_cK1 )
    nodeSwap.setA()
    nodeSwap.setL( nodeA )
    nodeSwap.setR( nodeB )
    nodeOther.setL( nodeC )
    nodeOther.setR( nodeD )
    nodeOther.setA()

    val nodeParUk = nodeSwap.getUkNext().map( z => nodeOther.getUkNext().head ++ z )
    val nodePar = Node( nodeParUk, nodeParCk )
    nodePar.setA()
    nodePar.setL( nodeSwap )
    nodePar.setR( nodeOther )

    assert( Node.satisfiesConstraintC( nodeA ) )
    assert( Node.satisfiesConstraintC( nodeB ) )
    assert( Node.satisfiesConstraintC( nodeC ) )
    assert( Node.satisfiesConstraintC( nodeD ) )
    assert( Node.satisfiesConstraintA( nodeSwap ) )
    assert( Node.satisfiesConstraintA( nodeOther ) )
    assert( Node.satisfiesConstraintA( nodePar ) )

    val nodeList = Transforms.trySwap( nodePar, nodeSwap )

    assert( Node.satisfiesConstraintA( nodeList(0) ) && nodeList(0).isA() )
    assert( Node.satisfiesConstraintA( nodeList(1) ) && nodeList(1).isA() )
    assert( Node.satisfiesConstraintA( nodeList(2) ) && nodeList(2).isA() )
  }

  @Test def testSwap6 {
    val nodeAuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 0 } )) // termination setA
    val nodeBuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 1 } )) // termination setB
    val node_cK = List.fill( nodeSize ) { myRand.nextInt( 2 ) - 1 }
    val nodeA = Node( nodeAuK, node_cK )
    val nodeB = Node( nodeBuK, node_cK )
    nodeA.setC()
    nodeB.setC()
    val nodeSwap = Node( nodeA.getUkNext(), nodeA.getCkNext() )
    val nodeOther = Node( nodeB.getUkNext(), nodeB.getCkNext() )
    nodeSwap.setB()
    nodeOther.setB()
    nodeSwap.setL( nodeA )
    nodeSwap.setR( nodeA )
    nodeOther.setL( nodeB )
    nodeOther.setR( nodeB )

    val nodeParUk = incr( nodeSwap.getUk().zip( nodeOther.getUk() ).map( z => z._1 ++ z._2 ) )
    val nodeParCk = nodeSwap.getCkNext()
    val nodePar = Node( nodeParUk, nodeParCk )
    nodePar.setA()
    nodePar.setL( nodeSwap )
    nodePar.setR( nodeOther )

    assert( Node.satisfiesConstraintC( nodeA ) )
    assert( Node.satisfiesConstraintC( nodeB ) )
    assert( Node.satisfiesConstraintB( nodeSwap ) )
    assert( Node.satisfiesConstraintB( nodeOther ) )
    assert( Node.satisfiesConstraintA( nodePar ) )

    val nodeList = Transforms.trySwap( nodePar, nodeSwap )

    assert( Node.satisfiesConstraintB( nodeList(0) ) && nodeList(0).isB() )
    assert( Node.satisfiesConstraintA( nodeList(1) ) && nodeList(1).isA() )
  }

  @Test def testSwap7 {
    val nodeAuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 0 } )) // termination setA
    val nodeBuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 1 } )) // termination setB
    val nodeCuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 2 } )) // termination setC
    val node_cK = List.fill( nodeSize ) { myRand.nextInt( 2 ) - 1 }
    val nodeA = Node( nodeAuK, node_cK )
    val nodeB = Node( nodeBuK, node_cK )
    val nodeC = Node( nodeCuK, node_cK )
    nodeA.setC()
    nodeB.setC()
    nodeC.setC()
    val nodeOtherUk = nodeB.getUkNext() ++ nodeC.getUkNext()
    val nodeSwap = Node( nodeA.getUkNext(), nodeA.getCkNext() )
    val nodeOther = Node( nodeOtherUk, nodeB.getCkNext().map( x => if ( x == -1 ) -1 else myRand.nextInt(2) ))
    nodeSwap.setB()
    nodeOther.setB()
    nodeSwap.setL( nodeA )
    nodeSwap.setR( nodeA )
    nodeOther.setL( nodeB )
    nodeOther.setR( nodeC )

    val nodeParUk = incr( nodeOther.getUk().map( z => nodeSwap.getUk().head ++ z ) )
    val nodeParCk = nodeOther.getCkNext()
    val nodePar = Node( nodeParUk, nodeParCk )
    nodePar.setA()
    nodePar.setL( nodeSwap )
    nodePar.setR( nodeOther )

    assert( Node.satisfiesConstraintC( nodeA ) )
    assert( Node.satisfiesConstraintC( nodeB ) )
    assert( Node.satisfiesConstraintC( nodeC ) )
    assert( Node.satisfiesConstraintB( nodeSwap ) )
    assert( Node.satisfiesConstraintB( nodeOther ) )
    assert( Node.satisfiesConstraintA( nodePar ) )

    val nodeList = Transforms.trySwap( nodePar, nodeSwap )

    assert( Node.satisfiesConstraintB( nodeList(0) ) && nodeList(0).isB() )
    assert( Node.satisfiesConstraintA( nodeList(1) ) && nodeList(1).isA() )
    assert( Node.satisfiesConstraintA( nodeList(2) ) && nodeList(2).isA() )
  }

  @Test def testSwap10 {
    val nodeAuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 0 } )) // termination setA
    val nodeBuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 1 } )) // termination setB
    val nodeParCk = List.fill( nodeSize ) { myRand.nextInt( 3 ) - 1 }
    val node_cK = nodeParCk.drop(2) ++ nodeParCk.take(2)
    val nodeA = Node( nodeAuK, node_cK.map( x => if ( x == 0 ) 0 else -1 ) )
    val nodeB = Node( nodeBuK, node_cK.map( x => if ( x == 1 ) 0 else -1 ) )
    nodeA.setC()
    nodeB.setC()
    val nodeSwap = Node( nodeA.getUkNext(), nodeA.getCkNext() )
    val nodeOther = Node( nodeB.getUkNext(), nodeB.getCkNext() )
    nodeSwap.setB()
    nodeSwap.setL( nodeA )
    nodeSwap.setR( nodeA )
    nodeOther.setB()
    nodeOther.setL( nodeB )
    nodeOther.setR( nodeB )

    val nodeParUk = nodeSwap.getUkNext() ++ nodeOther.getUkNext()
    val nodePar = Node( nodeParUk, nodeParCk )
    nodePar.setB()
    nodePar.setL( nodeSwap )
    nodePar.setR( nodeOther )

    assert( Node.satisfiesConstraintC( nodeA ) )
    assert( Node.satisfiesConstraintC( nodeB ) )
    assert( Node.satisfiesConstraintB( nodeSwap ) )
    assert( Node.satisfiesConstraintB( nodeOther ) )
    assert( Node.satisfiesConstraintB( nodePar ) )

    val nodeList = Transforms.trySwap( nodePar, nodeSwap )

    assert( Node.satisfiesConstraintB( nodeList(0) ) && nodeList(0).isB() )
    assert( Node.satisfiesConstraintB( nodeList(1) ) && nodeList(1).isB() )
  }

  @Test def testSwap11 {
    val nodeAuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 0 } )) // termination setA
    val nodeBuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 1 } )) // termination setB
    val nodeCuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 2 } )) // termination setC
    val nodeParCk = List.fill( nodeSize ) { myRand.nextInt( 3 ) - 1 }
    val node_cK = nodeParCk.drop(2) ++ nodeParCk.take(2)
    val nodeA = Node( nodeAuK, node_cK.map( x => if ( x == 0 ) 0 else -1 ) )
    val nodeB = Node( nodeBuK, node_cK.map( x => if ( x != -1 ) 0 else -1 ) )
    val nodeC = Node( nodeCuK, node_cK.map( x => if ( x == 1 ) 0 else -1 ) )
    nodeA.setC()
    nodeB.setC()
    nodeC.setC()
    val nodeSwap = Node( nodeA.getUkNext().map( z => nodeB.getUkNext().head ++ z ), nodeA.getCkNext() )
    val nodeOther = Node( nodeC.getUkNext().map( z => nodeB.getUkNext().head ++ z ), nodeC.getCkNext() )
    nodeSwap.setA()
    nodeSwap.setL( nodeA )
    nodeSwap.setR( nodeB )
    nodeOther.setA()
    nodeOther.setL( nodeB )
    nodeOther.setR( nodeC )

    val nodeParUk = nodeSwap.getUkNext() ++ nodeOther.getUkNext()
    val nodePar = Node( nodeParUk, nodeParCk )
    nodePar.setB()
    nodePar.setL( nodeSwap )
    nodePar.setR( nodeOther )

    assert( Node.satisfiesConstraintC( nodeA ) )
    assert( Node.satisfiesConstraintC( nodeB ) )
    assert( Node.satisfiesConstraintC( nodeC ) )
    assert( Node.satisfiesConstraintA( nodeSwap ) )
    assert( Node.satisfiesConstraintA( nodeOther ) )
    assert( Node.satisfiesConstraintB( nodePar ) )

    val nodeList = Transforms.trySwap( nodePar, nodeSwap )

    assert( Node.satisfiesConstraintA( nodeList(0) ) && nodeList(0).isA() )
    assert( Node.satisfiesConstraintB( nodeList(1) ) && nodeList(1).isB() )
    assert( Node.satisfiesConstraintB( nodeList(2) ) && nodeList(2).isB() )
  }

  @Test def testSwap12 {
    val nodeAuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 0 } )) // termination setA
    val nodeBuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 1 } )) // termination setB
    val nodeCuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 2 } )) // termination setC
    val nodeDuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 3 } )) // termination setC
    val nodeParCk = List.fill( nodeSize ) { myRand.nextInt( 5 ) - 1 }
    val node_cK = nodeParCk.drop(2) ++ nodeParCk.take(2)
    val node_cK1 = nodeParCk.drop(1) ++ nodeParCk.take(1)
    val nodeA = Node( nodeAuK, node_cK.map( x => if ( x == 0 ) 0 else -1 ) )
    val nodeB = Node( nodeBuK, node_cK.map( x => if ( x == 1 ) 0 else -1 ) )
    val nodeC = Node( nodeCuK, node_cK.map( x => if ( x == 2 ) 0 else -1 ) )
    val nodeD = Node( nodeDuK, node_cK.map( x => if ( x == 3 ) 0 else -1 ) )
    nodeA.setC()
    nodeB.setC()
    nodeC.setC()
    nodeD.setC()
    val nodeSwap = Node( nodeA.getUkNext() ++ nodeB.getUkNext(), node_cK1.map( x => if ( x != -1 && x < 2 ) x else -1 ) )
    val nodeOther = Node( nodeC.getUkNext() ++ nodeD.getUkNext(), node_cK1.map( x => if ( x < 2 ) -1 else x - 2 ) )
    nodeSwap.setB()
    nodeSwap.setL( nodeA )
    nodeSwap.setR( nodeB )
    nodeOther.setB()
    nodeOther.setL( nodeC )
    nodeOther.setR( nodeD )

    val nodeParUk = nodeSwap.getUkNext() ++ nodeOther.getUkNext()
    val nodePar = Node( nodeParUk, nodeParCk )
    nodePar.setB()
    nodePar.setL( nodeSwap )
    nodePar.setR( nodeOther )

    assert( Node.satisfiesConstraintC( nodeA ) )
    assert( Node.satisfiesConstraintC( nodeB ) )
    assert( Node.satisfiesConstraintC( nodeC ) )
    assert( Node.satisfiesConstraintC( nodeD ) )
    assert( Node.satisfiesConstraintB( nodeSwap ) )
    assert( Node.satisfiesConstraintB( nodeOther ) )
    assert( Node.satisfiesConstraintB( nodePar ) )

    val nodeList = Transforms.trySwap( nodePar, nodeSwap )

    assert( Node.satisfiesConstraintB( nodeList(0) ) && nodeList(0).isB() )
    assert( Node.satisfiesConstraintB( nodeList(1) ) && nodeList(1).isB() )
    assert( Node.satisfiesConstraintB( nodeList(2) ) && nodeList(2).isB() )
  }

  @Test def testSwap13 {
    val nodeAuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 0 } )) // termination setA
    val nodeBuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 1 } )) // termination setB
    val nodeCuK = List( Set( Vector( 0 ) ++ Vector.fill( dim - 1 ) { 2 } )) // termination setC
    val nodeParCk = List.fill( nodeSize ) { myRand.nextInt( 4 ) - 1 }
    val node_cK = nodeParCk.drop(2) ++ nodeParCk.take(2)
    val node_cK1 = nodeParCk.drop(1) ++ nodeParCk.take(1)
    val nodeA = Node( nodeAuK, node_cK.map( x => if ( x == 0 ) 0 else -1 ) )
    val nodeB = Node( nodeBuK, node_cK.map( x => if ( x == 1 ) 0 else -1 ) )
    val nodeC = Node( nodeCuK, node_cK.map( x => if ( x == 2 ) 0 else -1 ) )
    nodeA.setC()
    nodeB.setC()
    nodeC.setC()
    val nodeSwap = Node( nodeA.getUkNext(), node_cK1.map( x => if ( x == 0 ) 0 else -1 ) )
    val nodeOther = Node( nodeB.getUkNext() ++ nodeC.getUkNext(), node_cK1.map( x => if ( x < 1 ) -1 else x - 1 ) )
    nodeSwap.setB()
    nodeSwap.setL( nodeA )
    nodeSwap.setR( nodeA )
    nodeOther.setB()
    nodeOther.setL( nodeB )
    nodeOther.setR( nodeC )

    val nodeParUk = nodeSwap.getUkNext() ++ nodeOther.getUkNext()
    val nodeParCkComb = nodeSwap.getCkNext().zip( nodeOther.getCkNext() ).map( cks => {
      if ( cks._1 == -1 && cks._2 == -1 )
        -1
      else if ( cks._1 == -1 )
        cks._2 + 1
      else
        cks._1
    })
    val nodePar = Node( nodeParUk, nodeParCkComb )
    nodePar.setB()
    nodePar.setL( nodeSwap )
    nodePar.setR( nodeOther )

    assert( Node.satisfiesConstraintC( nodeA ) )
    assert( Node.satisfiesConstraintC( nodeB ) )
    assert( Node.satisfiesConstraintC( nodeC ) )
    assert( Node.satisfiesConstraintB( nodeSwap ) )
    assert( Node.satisfiesConstraintB( nodeOther ) )
    assert( Node.satisfiesConstraintB( nodePar ) )

    val nodeList = Transforms.trySwap( nodePar, nodeSwap )

    assert( Node.satisfiesConstraintB( nodeList(0) ) && nodeList(0).isB() )
    assert( Node.satisfiesConstraintB( nodeList(1) ) && nodeList(1).isB() )
    assert( Node.satisfiesConstraintB( nodeList(2) ) && nodeList(2).isB() )
  }

}