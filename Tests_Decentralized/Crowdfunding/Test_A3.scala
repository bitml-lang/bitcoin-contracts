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

class Test_A3  extends AnyFunSuite with BeforeAndAfterAll  {
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

  test("A3") {
    //get the initial state from the setup function
    val initialState = Setup.setup()
    val stateJson = new Serializer().prettyPrintState(initialState)

    // Declare client manager
    val cm = ClientManager()

    //creating akka actor system
    val a3 = cm.createActor(testSystem, "A3")

    //private and public key of the partecipant
    val a3_priv = PrivateKey.fromBase58("cSmRqDGe8UoQy4jmhPU7c88iCJ6V7uV8EPJ9b194bBX5JqH2YQN5", Base58.Prefix.SecretKeyTestnet)._1
    val a3_pub = a3_priv.publicKey

    //fetch the partecipant db from the initial state
    val a3_p = initialState.partdb.fetch(a3_pub.toString()).get

    // Initialize Alice with the state information.
    a3 ! Init(a3_priv, stateJson)

    // Start network interface.
    a3 ! Listen("test_application_a3.conf", a3_p.endpoint.system)

    while(true) {
      println("Authorizing signature for venture")
      a3 ! Authorize("V")
      Thread.sleep(5000)
    }

    // final partecipant shutdown
    a3 ! StopListening()
    cm.shutSystem(testSystem)
  }

}
