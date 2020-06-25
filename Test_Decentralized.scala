import akka.actor.{ActorSystem, Address, CoordinatedShutdown, Props}
import akka.util.Timeout
import fr.acinq.bitcoin.Crypto.{PrivateKey, PublicKey}
import fr.acinq.bitcoin.{Base58, Base58Check, Btc, Crypto, OP_0, OP_PUSHDATA, Satoshi, Script, Transaction}
import akka.pattern.ask
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import scodec.bits.ByteVector
import xyz.bitml.api.{ChunkEntry, ChunkPrivacy, ChunkType, Client, IndexEntry, Participant, TxEntry}
import xyz.bitml.api.messaging.{AskForSigs, AssembledTx, CurrentState, DumpState, Init, Listen, SearchTx, StopListening, TryAssemble}
import xyz.bitml.api.persistence.{MetaStorage, ParticipantStorage, State, TxStorage}
import xyz.bitml.api.serialization.Serializer

import scala.concurrent.duration._
import scala.concurrent.Await

class Test_Decentralized extends AnyFunSuite with BeforeAndAfterAll {

  private var testSystem: ActorSystem = _

  override def beforeAll(): Unit = {
    testSystem = ActorSystem(name = "internalTestSystem")
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
    super.afterAll()
  }

  test("Oracle Timed Version - Withdraw Bob") {
    /*
    /*
 * Oracle (timed version)
 *
 * https://blockchain.unica.it/balzac/docs/oracle.html
 */

// tx with Alice's funds, redeemable with Alice's private key
transaction A_funds {
    input = _
    output = 1 BTC: fun(x). versig(Alice.kApub; x)
}

participant Alice {
    // Alice's private key
    private const kA = key:cSthBXr8YQAexpKeh22LB9PdextVE1UJeahmyns5LzcmMDSy59L4
    // Alice's public key
    const kApub = kA.toPubkey
    // deadline
    const deadline = 2018-12-31

    transaction T {
        input = A_funds: sig(kA)
        output = 1 BTC: fun(sigma, sigO).
                versig(Bob.kBpub, Oracle.kOpub; sigma, sigO)
                || checkDate deadline : versig(kApub;sigma)
    }

    // Alice takes back her deposit after the deadline
    transaction T1 {
        input = T: sig(kA) _
        output = 1 BTC: fun(sigA). versig(kApub;sigA)
        absLock = date deadline
    }
}

participant Bob {
    // Bob's private key
    private const kB = key:cQmSz3Tj3usor9byskhpCTfrmCM5cLetLU9Xw6y2csYhxSbKDzUn
    // Bob's public key
    const kBpub = kB.toPubkey

    transaction T1(sigOtimed) {
        input = Alice.T: sig(kB) sigOtimed
        output = 1 BTC: fun(x). versig(kB; x)
    }
}

participant Oracle {
    // Oracle's private key
    private const kO = key:cTyxEAoUSKcC9NKFCjxKTaXzP8i1ufEKtwVVtY6AsRPpRgJTZQRt
    // Oracle's public key
    const kOpub = kO.toPubkey

    const sigO = sig(kO) of Bob.T1(_)
}


eval Alice.T, Alice.T1, Bob.T1(Oracle.sigO)

     */

    // creating 3 Transaction objects using the serialized balzac transactions
    val t = Transaction.read("02000000017875f000ee77e9adac3224ad43c977b22b02f65339b7c69e1d7780f92e2e7fcb000000006a473044022078f74bc86bb9ff3872fa824eff9ac0bf325c5a137f0cff1c5812fd85676cb40002203a7d7a6315d7de5cd389d07c6ac67e315b3e1388a5ac2aa1066331828560b8b1012103ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c3ffffffff0100e1f5050000000017a91452155b181071f4df49dea8bc0aa1c048772a2fa98700000000")
    val alice_t1 = Transaction.read("02000000016379aa276cab7ebd2f7677d3eb73bf7178ee646a17b29cf85ff8a9133f83b5f300000000cd47304402200e6d64cc3b54f44f938e995889e2b6d863851cab1bc9915b611c386442639e93022057e0647875a9a81246fc8024b672ed903eaee93cae5a4b9ba79824e21b826a3901004c826b6b006c766c766b7c6b522103859a0f601cf485a72ec097fddd798c694b0257f69f0229506f8ea923bc600c5e210237c53ebef2992c5b3f0efca8b849f4969095b31e597bdab292385bb132c30f3e52ae63516704005c295cb1756c766b2103ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c3ac68feffffff0100e1f505000000001976a914448f9bd84d520fb00adb83f26d8a78ddc5403c8988ac005c295c")
    val bob_t1 = Transaction.read("02000000016379aa276cab7ebd2f7677d3eb73bf7178ee646a17b29cf85ff8a9133f83b5f300000000fd150147304402200e5f07c09238ae90c3a58661763bf67dee619b717b5bbf2137c73d5c01fb53cc022055325b86d3e4911280dac2c29a5ea990b65e1c39418b03ff83e11a804621c58201483045022100d294cf1b34a08e9e6573af8fb31b8c46dee370e88bc13172091fbcd7a62907d6022040aa1c435fec77285478dc2c89132da2ec1d4fefa5d2e784314411b7317b9d98014c826b6b006c766c766b7c6b522103859a0f601cf485a72ec097fddd798c694b0257f69f0229506f8ea923bc600c5e210237c53ebef2992c5b3f0efca8b849f4969095b31e597bdab292385bb132c30f3e52ae63516704005c295cb1756c766b2103ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c3ac68ffffffff0100e1f505000000001976a914ba91ed34ad92a7c2aa2d764c73cd0f31a18df68088ac00000000")

    //creating a transaction storage in which we put the transactions for later use of the partecipants
    val dbx = new TxStorage()
    dbx.save("T", t)
    dbx.save("T1_alice", alice_t1)
    dbx.save("T1_bob", bob_t1)

    //declaring the private and public keys for each partecipant
    val privA = PrivateKey.fromBase58("cSthBXr8YQAexpKeh22LB9PdextVE1UJeahmyns5LzcmMDSy59L4", Base58.Prefix.SecretKeyTestnet)._1
    val pubA = privA.publicKey
    val privB = PrivateKey.fromBase58("cQmSz3Tj3usor9byskhpCTfrmCM5cLetLU9Xw6y2csYhxSbKDzUn", Base58.Prefix.SecretKeyTestnet)._1
    val pubB = privB.publicKey
    val privO = PrivateKey.fromBase58("cTyxEAoUSKcC9NKFCjxKTaXzP8i1ufEKtwVVtY6AsRPpRgJTZQRt", Base58.Prefix.SecretKeyTestnet)._1
    val pubO = privO.publicKey

    //creeating an endpoint for each partecipant in order for them to communicate on a specific port
    val alice_p = Participant("Alice", List(pubA), Address("akka", "test", "127.0.0.1", 25000))
    val bob_p = Participant("Bob", List(pubB), Address("akka", "test", "127.0.0.1", 25001))
    val oracle_p = Participant("Oracle" , List(pubO), Address("akka", "test", "127.0.0.1", 25002))

    //saving the partecipants on a storage
    val partDb = new ParticipantStorage()
    partDb.save(alice_p)
    partDb.save(bob_p)
    partDb.save(oracle_p)

    /*
    chunks are used to identify specific parts of a serialized transaction so that the partecipants can use them and share them
    if needed. Every ChunkEntry has a type that represents what that part of the tx means (like signature, secret or other)
    also there is a privacy that can be public (visible to every partecipant) auth or private. The index is used to identify
    which signature or secret of the transaction we are referring to. For instance id we have two signatures in a transaction
    we must be careful to identify them with the correct index. the owner and data field are pretty much self-explaintory
    */
    //creating chunks for tx T in which we have a P2PKH signature owned by Alice on index 0 and of public domain.
    val t_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2PKH, chunkPrivacy = ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(pubA), data = ByteVector.empty))

    //creating chunks for T1_bob, in this case we have two p2sh signatures owned by bob and the oracle. the indexes are
    //based on the order of the signature in the transaction (so first in the tx we have bob signature and second the oracle one)
    val t1_bob_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy = ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(pubB), data = ByteVector.empty),
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy = ChunkPrivacy.PUBLIC, chunkIndex = 1, owner = Option(pubO), data = ByteVector.empty))

    //chunk for T1_alice with just Alice's signature
    val t1_alice_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy = ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(pubA), data = ByteVector.empty))

    //now we create some entries for each transaction with additional info, the amount of bitcoins that are spent in the tx
    val t_entry = TxEntry(name = "T", indexData = Map(0 -> IndexEntry(amt = Btc(1).toSatoshi ,chunkData = t_chunks)))
    val t1_bob_entry = TxEntry(name = "T1_bob", indexData = Map(0 -> IndexEntry(amt = Btc(1).toSatoshi ,chunkData = t1_bob_chunks)))
    val t1_alice_entry = TxEntry(name = "T1_alice", indexData = Map(0 -> IndexEntry(amt = Btc(1).toSatoshi, chunkData = t1_alice_chunks)))

    //now we save the entries containing the chunks and metadata into a meta storage
    val metadb = new MetaStorage()
    metadb.save(t_entry)
    metadb.save(t1_bob_entry)
    metadb.save(t1_alice_entry)

    //now we finally create a starting state for the partecipants containing all the previous infos
    val ser = new Serializer()
    val startingState = ser.prettyPrintState(State(partDb, dbx, metadb))
    // println(startingState)

    //creating akka actors
    val alice = testSystem.actorOf(Props(classOf[Client]))
    val bob = testSystem.actorOf(Props(classOf[Client]))
    val oracle = testSystem.actorOf(Props(classOf[Client]))

    // initializing the state of akka actors
    alice ! Init(privA , startingState)
    bob ! Init(privB , startingState)
    oracle ! Init(privO, startingState)

    // Start their network interfaces.
    alice ! Listen("test_application.conf", alice_p.endpoint.system)
    bob ! Listen("test_application_b.conf", bob_p.endpoint.system)
    oracle ! Listen("test_application_o.conf", oracle_p.endpoint.system)

    //we declare an arbitrary timeout
    implicit val timeout : Timeout = Timeout(2000 milliseconds)

    //dump alice's state
    val future = alice ? DumpState()
    val res = ser.loadState(Await.result((future), timeout.duration).asInstanceOf[CurrentState].state)

    //alice tries to assemble T and also publish it in the testnet
    val future2 = alice ? TryAssemble("T", autoPublish = false)
    //val tx = alice ! SearchTx("T")
    //println(tx)
    val res2 = Await.result(future2, timeout.duration).asInstanceOf[AssembledTx].serializedTx
    val tx = Transaction.read(res2)


    //bob tries to assemble T1 and is gonna ask the oracle signature
    bob ! AskForSigs("T1_bob")
    oracle ! AskForSigs("T1_bob")
    Thread.sleep(500)

    val future3 = bob ? TryAssemble("T1_bob", autoPublish = true)
    Thread.sleep(2000)
    bob ! SearchTx("T1_bob")
    Thread.sleep(2000)
    //val res3 = Await.result(future3, timeout.duration).asInstanceOf[AssembledTx].serializedTx
    //val res3 = Await.result(future3, timeout.duration).asInstanceOf[AssembledTx].serializedTx
    //val t1_bob = Transaction.read(res3)
    //println(t1_bob.txIn+"\n"+t1_bob.txOut)

    // final partecipants shutdown
    for (participant <- Seq(alice, bob, oracle)) {
      participant ! StopListening()
    }
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
  }

  test("Alice") {
    // creating 2 Transaction objects using the serialized balzac transactions
    val t = Transaction.read("02000000017875f000ee77e9adac3224ad43c977b22b02f65339b7c69e1d7780f92e2e7fcb000000006a473044022078f74bc86bb9ff3872fa824eff9ac0bf325c5a137f0cff1c5812fd85676cb40002203a7d7a6315d7de5cd389d07c6ac67e315b3e1388a5ac2aa1066331828560b8b1012103ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c3ffffffff0100e1f5050000000017a91452155b181071f4df49dea8bc0aa1c048772a2fa98700000000")
    val alice_t1 = Transaction.read("02000000016379aa276cab7ebd2f7677d3eb73bf7178ee646a17b29cf85ff8a9133f83b5f300000000cd47304402200e6d64cc3b54f44f938e995889e2b6d863851cab1bc9915b611c386442639e93022057e0647875a9a81246fc8024b672ed903eaee93cae5a4b9ba79824e21b826a3901004c826b6b006c766c766b7c6b522103859a0f601cf485a72ec097fddd798c694b0257f69f0229506f8ea923bc600c5e210237c53ebef2992c5b3f0efca8b849f4969095b31e597bdab292385bb132c30f3e52ae63516704005c295cb1756c766b2103ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c3ac68feffffff0100e1f505000000001976a914448f9bd84d520fb00adb83f26d8a78ddc5403c8988ac005c295c")

    //creating a transaction storage in which we put the transactions for later use of the partecipants
    val dbx = new TxStorage()
    dbx.save("T", t)
    dbx.save("T1_alice", alice_t1)

    //declaring the private and public keys
    val privA = PrivateKey.fromBase58("cSthBXr8YQAexpKeh22LB9PdextVE1UJeahmyns5LzcmMDSy59L4", Base58.Prefix.SecretKeyTestnet)._1
    val pubA = PublicKey(ByteVector.fromValidHex("03ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c3"))
    val pubB = PublicKey(ByteVector.fromValidHex("03859a0f601cf485a72ec097fddd798c694b0257f69f0229506f8ea923bc600c5e"))
    val pubO = PublicKey(ByteVector.fromValidHex("0237c53ebef2992c5b3f0efca8b849f4969095b31e597bdab292385bb132c30f3e"))

    //creeating an endpoint for each partecipant in order for them to communicate on a specific port
    val alice_p = Participant("Alice", List(pubA), Address("akka", "test", "127.0.0.1", 25000))
    val bob_p = Participant("Bob", List(pubB), Address("akka", "test", "127.0.0.1", 25001))
    val oracle_p = Participant("Oracle" , List(pubO), Address("akka", "test", "127.0.0.1", 25002))

    //saving the partecipants on a storage
    val partDb = new ParticipantStorage()
    partDb.save(alice_p)
    partDb.save(bob_p)
    partDb.save(oracle_p)

    //creating chunks for tx T in which we have a P2PKH signature owned by Alice on index 0 and of public domain.
    val t_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2PKH, chunkPrivacy = ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(pubA), data = ByteVector.empty))

    //chunk for T1_alice with just Alice's signature
    val t1_alice_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy = ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(pubA), data = ByteVector.empty))

    //now we create some entries for each transaction with additional info, the amount of bitcoins that are spent in the tx
    val t_entry = TxEntry(name = "T", indexData = Map(0 -> IndexEntry(amt = Btc(1).toSatoshi ,chunkData = t_chunks)))
    val t1_alice_entry = TxEntry(name = "T1_alice", indexData = Map(0 -> IndexEntry(amt = Btc(1).toSatoshi, chunkData = t1_alice_chunks)))

    //now we save the entries containing the chunks and metadata into a meta storage
    val metadb = new MetaStorage()
    metadb.save(t_entry)
    metadb.save(t1_alice_entry)

    //now we finally create a starting state for the partecipants containing all the previous infos
    val ser = new Serializer()
    val startingState = ser.prettyPrintState(State(partDb, dbx, metadb))

    //creating akka actors
    val alice = testSystem.actorOf(Props(classOf[Client]))
    val bob = testSystem.actorOf(Props(classOf[Client]))
    val oracle = testSystem.actorOf(Props(classOf[Client]))

    // Start network interface.
    alice ! Listen("test_application.conf", alice_p.endpoint.system)

    //we declare an arbitrary timeout
    implicit val timeout : Timeout = Timeout(2000 milliseconds)

    //alice tries to assemble T and also publish it in the testnet
    val future2 = alice ? TryAssemble("T", autoPublish = false)
    //val tx = alice ! SearchTx("T")
    //println(tx)
    val res2 = Await.result(future2, timeout.duration).asInstanceOf[AssembledTx].serializedTx
    val tx = Transaction.read(res2)

    // final partecipant shutdown
    alice ! StopListening()
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
  }

  test("Bob") {
    // creating a Transaction object using the serialized balzac transaction
    val bob_t1 = Transaction.read("02000000016379aa276cab7ebd2f7677d3eb73bf7178ee646a17b29cf85ff8a9133f83b5f300000000fd150147304402200e5f07c09238ae90c3a58661763bf67dee619b717b5bbf2137c73d5c01fb53cc022055325b86d3e4911280dac2c29a5ea990b65e1c39418b03ff83e11a804621c58201483045022100d294cf1b34a08e9e6573af8fb31b8c46dee370e88bc13172091fbcd7a62907d6022040aa1c435fec77285478dc2c89132da2ec1d4fefa5d2e784314411b7317b9d98014c826b6b006c766c766b7c6b522103859a0f601cf485a72ec097fddd798c694b0257f69f0229506f8ea923bc600c5e210237c53ebef2992c5b3f0efca8b849f4969095b31e597bdab292385bb132c30f3e52ae63516704005c295cb1756c766b2103ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c3ac68ffffffff0100e1f505000000001976a914ba91ed34ad92a7c2aa2d764c73cd0f31a18df68088ac00000000")

    //creating a transaction storage in which we put the transactions for later use of the partecipants
    val dbx = new TxStorage()
    dbx.save("T1_bob", bob_t1)

    //declaring the private and public keys
    val privB = PrivateKey.fromBase58("cQmSz3Tj3usor9byskhpCTfrmCM5cLetLU9Xw6y2csYhxSbKDzUn", Base58.Prefix.SecretKeyTestnet)._1
    val pubA = PublicKey(ByteVector.fromValidHex("03ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c3"))
    val pubB = PublicKey(ByteVector.fromValidHex("03859a0f601cf485a72ec097fddd798c694b0257f69f0229506f8ea923bc600c5e"))
    val pubO = PublicKey(ByteVector.fromValidHex("0237c53ebef2992c5b3f0efca8b849f4969095b31e597bdab292385bb132c30f3e"))


    //creeating an endpoint for each partecipant in order for them to communicate on a specific port
    val alice_p = Participant("Alice", List(pubA), Address("akka", "test", "127.0.0.1", 25000))
    val bob_p = Participant("Bob", List(pubB), Address("akka", "test", "127.0.0.1", 25001))
    val oracle_p = Participant("Oracle" , List(pubO), Address("akka", "test", "127.0.0.1", 25002))

    //saving the partecipants on a storage
    val partDb = new ParticipantStorage()
    partDb.save(alice_p)
    partDb.save(bob_p)
    partDb.save(oracle_p)

    //creating chunks for T1_bob, in this case we have two p2sh signatures owned by bob and the oracle. the indexes are
    //based on the order of the signature in the transaction (so first in the tx we have bob signature and second the oracle one)
    val t1_bob_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy = ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(pubB), data = ByteVector.empty),
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy = ChunkPrivacy.PUBLIC, chunkIndex = 1, owner = Option(pubO), data = ByteVector.empty))

    //t1 entry with bob chunks
    val t1_bob_entry = TxEntry(name = "T1_bob", indexData = Map(0 -> IndexEntry(amt = Btc(1).toSatoshi ,chunkData = t1_bob_chunks)))

    //now we save the entry containing the chunks and metadata into a meta storage
    val metadb = new MetaStorage()
    metadb.save(t1_bob_entry)

    //now we finally create a starting state for the partecipants containing all the previous infos
    val ser = new Serializer()
    val startingState = ser.prettyPrintState(State(partDb, dbx, metadb))

    //creating akka actors
    val alice = testSystem.actorOf(Props(classOf[Client]))
    val bob = testSystem.actorOf(Props(classOf[Client]))
    val oracle = testSystem.actorOf(Props(classOf[Client]))

    // Start network interface.
    bob ! Listen("test_application_b.conf", bob_p.endpoint.system)

    //we declare an arbitrary timeout
    implicit val timeout : Timeout = Timeout(2000 milliseconds)

    //bob tries to assemble T1 and is gonna ask the oracle signature
    bob ! AskForSigs("T1_bob")
    oracle ! AskForSigs("T1_bob")
    Thread.sleep(500)

    val future3 = bob ? TryAssemble("T1_bob", autoPublish = true)
    Thread.sleep(2000)

    // final partecipant shutdown
    bob ! StopListening()
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
  }

  test("Oracle") {
    //declaring the private and public keys
    val privO = PrivateKey.fromBase58("cTyxEAoUSKcC9NKFCjxKTaXzP8i1ufEKtwVVtY6AsRPpRgJTZQRt", Base58.Prefix.SecretKeyTestnet)._1
    val pubA = PublicKey(ByteVector.fromValidHex("03ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c3"))
    val pubB = PublicKey(ByteVector.fromValidHex("03859a0f601cf485a72ec097fddd798c694b0257f69f0229506f8ea923bc600c5e"))
    val pubO = PublicKey(ByteVector.fromValidHex("0237c53ebef2992c5b3f0efca8b849f4969095b31e597bdab292385bb132c30f3e"))


    //creeating an endpoint for each partecipant in order for them to communicate on a specific port
    val alice_p = Participant("Alice", List(pubA), Address("akka", "test", "127.0.0.1", 25000))
    val bob_p = Participant("Bob", List(pubB), Address("akka", "test", "127.0.0.1", 25001))
    val oracle_p = Participant("Oracle" , List(pubO), Address("akka", "test", "127.0.0.1", 25002))

    //saving the partecipants on a storage
    val partDb = new ParticipantStorage()
    partDb.save(alice_p)
    partDb.save(bob_p)
    partDb.save(oracle_p)

    //now we finally create a starting state for the partecipants containing all the previous infos
    val ser = new Serializer()
    val startingState = ser.prettyPrintState(State(partDb))

    //creating akka actors
    val alice = testSystem.actorOf(Props(classOf[Client]))
    val bob = testSystem.actorOf(Props(classOf[Client]))
    val oracle = testSystem.actorOf(Props(classOf[Client]))

    // Start network interface.
    oracle ! Listen("test_application_o.conf", oracle_p.endpoint.system)

    //we declare an arbitrary timeout
    implicit val timeout : Timeout = Timeout(2000 milliseconds)

    // final partecipant shutdown
    oracle ! StopListening()
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
  }
}
