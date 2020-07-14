package Tests_Decentralized

import Tests_DecentralizedEscrow.ClientManager
import akka.actor.{ActorSystem, Address, CoordinatedShutdown, Props}
import akka.util.Timeout
import fr.acinq.bitcoin.Crypto.{PrivateKey, PublicKey}
import fr.acinq.bitcoin._
import akka.pattern.ask
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import scodec.bits.ByteVector
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient
import xyz.bitml.api.{ChunkEntry, ChunkPrivacy, ChunkType, Client, IndexEntry, Participant, TxEntry}
import xyz.bitml.api.messaging.{AskForSigs, AssembledTx, Authorize, CurrentState, DumpState, Init, Listen, SearchTx, StopListening, TryAssemble}
import xyz.bitml.api.persistence.{MetaStorage, ParticipantStorage, State, TxStorage}
import xyz.bitml.api.serialization.Serializer

import scala.concurrent.duration._
import scala.concurrent.Await

class Test_A1 extends AnyFunSuite with BeforeAndAfterAll {

  private var testSystem: ActorSystem = _

  override def beforeAll(): Unit = {
    testSystem = ActorSystem(name = "internalTestSystemA1")
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
    super.afterAll()
  }

  test("A1") {
    //get the initial state from the setup function
    val initialState = Setup.setup()
    val stateJson = new Serializer().prettyPrintState(initialState)

    // Declare client manager
    val cm = ClientManager()

    //creating akka actor system
    val a1 = cm.createActor(testSystem, "A1")

    //private and public key of the partecipant
    val a1_priv = PrivateKey.fromBase58("cVbFzgZSpnuKvNT5Z3DofF9dV4Dr1zFQJw9apGZDVaG73ULqM7XS", Base58.Prefix.SecretKeyTestnet)._1
    val a1_pub = a1_priv.publicKey

    //fetch the partecipant db from the initial state
    val a1_p = initialState.partdb.fetch(a1_pub.toString()).get

    // Initialize Alice with the state information.
    a1 ! Init(a1_priv, stateJson)

    // Start network interface.
    a1 ! Listen("test_application_a1.conf", a1_p.endpoint.system)

    while(true) {
      println("Authorizing signature for venture")
      a1 ! Authorize("V")
      Thread.sleep(5000)
    }

    // final partecipant shutdown
    a1 ! StopListening()
    cm.shutSystem(testSystem)
  }
}