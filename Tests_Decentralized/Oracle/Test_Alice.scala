package Tests_Decentralized

import Managers.{ClientManager, Helpers}
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
import xyz.bitml.api.messaging.{AskForSigs, AssembledTx, CurrentState, DumpState, Init, Listen, SearchTx, StopListening, TryAssemble}
import xyz.bitml.api.persistence.{MetaStorage, ParticipantStorage, State, TxStorage}
import xyz.bitml.api.serialization.Serializer
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.Await

class Test_Alice extends AnyFunSuite with BeforeAndAfterAll with LazyLogging {

  private var testSystem: ActorSystem = _

  override def beforeAll(): Unit = {
    testSystem = ActorSystem(name = "internalTestSystem1")
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
    val log = Helpers.CustomLogger("Alice")

    log.printStatus("Contract started - Executing Setup")
    //get the initial state from the setup function
    val initialState = Setup.setup()
    val stateJson = new Serializer().prettyPrintState(initialState)

    // Declare client manager
    val cm = ClientManager()

    // Create actor
    log.printStatus("Actor System Creation!")
    val contract = cm.createActor(testSystem, "Alice")

    //private and public key of the partecipant
    val a_priv = PrivateKey.fromBase58("cVbFzgZSpnuKvNT5Z3DofF9dV4Dr1zFQJw9apGZDVaG73ULqM7XS", Base58.Prefix.SecretKeyTestnet)._1
    val a_pub = a_priv.publicKey


    //fetch the partecipant db from the initial state
    log.printStatus("Fetching contract partecipants from Setup")
    val alice_p = initialState.partdb.fetch(a_pub.toString()).get


    // Initialize Alice with the state information.
    log.printStatus("Contract initiated with private key and initial state")
    contract ! Init(a_priv, stateJson)


    // Start network interface.
    log.printStatus("Starting newtork interface - Listening incoming traffic on port "+ alice_p.endpoint.port.get)
    contract ! Listen("test_application.conf", alice_p.endpoint.system)

    //we declare an arbitrary timeout
    implicit val timeout : Timeout = Timeout(2000 milliseconds)


    //alice tries to assemble T and also publish it in regtest
    log.printStatus("Trying to assemble and publish transaction T")
    val future = contract ? TryAssemble("T", autoPublish = true)
    val res = cm.getResult(future)
    log.printStatus("Assembled T with raw " + res)
    val published = cm.isPublished(res)
    if (published) log.printStatus("Publish successfull.. Contract executed successfully") else log.printStatus("Publish failed.. retrying")
    // final partecipant shutdown
    log.printStatus("Shutting Down")
    contract ! StopListening()
    cm.shutSystem(testSystem)
  }
}