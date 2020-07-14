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

class Test_Bob  extends AnyFunSuite with BeforeAndAfterAll  {
  private var testSystem: ActorSystem = _

  override def beforeAll(): Unit = {
    testSystem = ActorSystem(name = "internalTestSystemBob")
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
    super.afterAll()
  }

  test("Bob") {
    // get the initial state from the setup function
    val initialState = Setup.setup()
    val stateJson = new Serializer().prettyPrintState(initialState)

    // Declare client manager
    val cm = ClientManager()

    // creating akka actor
    val bob = cm.createActor(testSystem, "Bob")

    // private and public key
    val b_priv = PrivateKey.fromBase58("cPU3AmQFsBxvrBgTWc1j3pS6T7m4bYWMFQyPnR9Qp3o3UTCBwspZ", Base58.Prefix.SecretKeyTestnet)._1
    val b_pub = PublicKey(ByteVector.fromValidHex("028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7"))

    // fetch the partecipant db from the initial state
    val bob_p = initialState.partdb.fetch(b_pub.toString()).get

    // Initialize Alice with the state information.
    bob ! Init(b_priv, stateJson)

    // Start network interface.
    bob ! Listen("test_application_b.conf", bob_p.endpoint.system)

    // we declare an arbitrary timeout
    implicit val timeout: Timeout = Timeout(2000 milliseconds)

    var notReceived = true

    // Bob should keep trying to publish the transaction until he receives Oracle's signature
    while (notReceived) {
      if(true) {
        try {

          // Ask for signature
          bob ! AskForSigs("T1_bob")
          // Try to assemble the transaction and publish it
          bob ? TryAssemble("T1_bob", autoPublish = true)

          bob ! AskForSigs("T1_C_bob")
          bob ? TryAssemble("T1_C_bob", autoPublish = true)

          bob ! AskForSigs("T1_C_split_bob")
          bob ? TryAssemble("T1_C_split_bob", autoPublish = true)

        } catch {
          // If something goes wrong we will retry
          case x: Exception => {
            println("Publish Failed... Retrying")
            notReceived = true
          }
        }
      } else {
        println("Refunding alice")
        bob ! Authorize("T1_alice")
      }
      Thread.sleep(4000)
    }

    // final partecipant shutdown
    bob ! StopListening()
    cm.shutSystem(testSystem)
  }

}
