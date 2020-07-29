package Tests_Decentralized

import Managers.ClientManager
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

class Test_Alice extends AnyFunSuite with BeforeAndAfterAll {

  private var testSystem: ActorSystem = _

  override def beforeAll(): Unit = {
    testSystem = ActorSystem(name = "internalTestSystemAlice")
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
    super.afterAll()
  }

  /*
  participant Alice {
    // Alice's private key
    private const kA = key:cSthBXr8YQAexpKeh22LB9PdextVE1UJeahmyns5LzcmMDSy59L4
    // Alice's public key
    const kApub = kA.toPubkey

    transaction T {
        input = A_funds: sig(kA)
        output = 1 BTC: fun(sigB, sigO). versig(Bob.kBpub, Oracle.kOpub; sigB, sigO)
    }
}
   */
  test("Alice") {
    //get the initial state from the setup function
    val initialState = Setup.setup()
    val stateJson = new Serializer().prettyPrintState(initialState)

    // Declare the client manager
    val cm = ClientManager()

    //creating akka actor
    val alice = cm.createActor(testSystem, "Alice")

    //private and public key of the partecipant
    val a_priv = PrivateKey.fromBase58("cVbFzgZSpnuKvNT5Z3DofF9dV4Dr1zFQJw9apGZDVaG73ULqM7XS", Base58.Prefix.SecretKeyTestnet)._1
    //val a_pub = PublicKey(ByteVector.fromValidHex("03fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a"))
    val a_pub = a_priv.publicKey

    //fetch the partecipant db from the initial state
    val alice_p = initialState.partdb.fetch(a_pub.toString()).get

    // Initialize Alice with the state information.
    alice ! Init(a_priv, stateJson)

    // Start network interface.
    alice ! Listen("test_application.conf", alice_p.endpoint.system)

    //we declare an arbitrary timeout
    implicit val timeout : Timeout = Timeout(2000 milliseconds)

    //alice tries to assemble T and also publish it in the testnet
    val future = alice ? TryAssemble("T", autoPublish = true)
    //alice has produced a transaction
    val res = cm.getResult(future)
    cm.rpc.decodeRawTransaction(res)
    //print the serialized transaction
    println(res)

    while(true) {
      if(false) {
        println("Giving signature")
        alice ! Authorize("T1_bob")
      } else {
        try {
          // Ask for signature
          alice ! AskForSigs("T1_alice")
          // Try to assemble the transaction and publish it
          alice ? TryAssemble("T1_alice", autoPublish = true)

          alice ! AskForSigs("T1_C_alice")
          alice ? TryAssemble("T1_C_alice", autoPublish = true)

          alice ! AskForSigs("T1_C_split_alice")
          alice ? TryAssemble("T1_C_split_alice", autoPublish = true)

        } catch {
          case x : Exception => {
            println("Publishing failed...retrying")
          }
        }
      }
      Thread.sleep(5000)
    }

    // final partecipant shutdown
    alice ! StopListening()
    cm.shutSystem(testSystem)
  }
}