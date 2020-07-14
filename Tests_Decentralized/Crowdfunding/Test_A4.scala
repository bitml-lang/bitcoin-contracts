package Tests_Decentralized

import Tests_DecentralizedEscrow.ClientManager
import akka.actor.{ActorSystem, CoordinatedShutdown, Props}
import fr.acinq.bitcoin.Crypto.PrivateKey
import fr.acinq.bitcoin._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import xyz.bitml.api.Client
import xyz.bitml.api.messaging.{Authorize, Init, Listen, StopListening}
import xyz.bitml.api.serialization.Serializer

class Test_A4  extends AnyFunSuite with BeforeAndAfterAll  {
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

  test("A4") {
    //get the initial state from the setup function
    val initialState = Setup.setup()
    val stateJson = new Serializer().prettyPrintState(initialState)

    // Declare client manager
    val cm = ClientManager()

    //creating akka actor system
    val a4 = cm.createActor(testSystem, "A4")

    //private and public key of the partecipant
    val a4_priv = PrivateKey.fromBase58("cTnoNcfq1S3xYuAZnGWictR1FRNxsuVChUK9iHXpVa3EEg8Y5YxG", Base58.Prefix.SecretKeyTestnet)._1
    val a4_pub = a4_priv.publicKey

    //fetch the partecipant db from the initial state
    val a4_p = initialState.partdb.fetch(a4_pub.toString()).get

    // Initialize Alice with the state information.
    a4 ! Init(a4_priv, stateJson)

    // Start network interface.
    a4 ! Listen("test_application_a4.conf", a4_p.endpoint.system)

    while(true) {
      println("Authorizing signature for venture")
      a4 ! Authorize("V")
      Thread.sleep(5000)
    }

    // final partecipant shutdown
    a4 ! StopListening()
    cm.shutSystem(testSystem)
  }

}
