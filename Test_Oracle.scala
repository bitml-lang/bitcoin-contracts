

import scala.concurrent.duration._
import akka.actor.{ActorSystem, Address, CoordinatedShutdown, Props}
import akka.pattern.ask
import akka.util.Timeout
import fr.acinq.bitcoin.Crypto.{PrivateKey, PublicKey}
import fr.acinq.bitcoin.{Base58, Btc, OP_0, Satoshi, Script, ScriptElt, ScriptFlags, Transaction}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import scodec.bits.ByteVector
import xyz.bitml.api.messaging.{AskForSigs, AssembledTx, CurrentState, DumpState, Init, Internal, Listen, Ping, Pong, PreInit, StopListening, TryAssemble}
import xyz.bitml.api.{ChunkEntry, ChunkPrivacy, ChunkType, Client, IndexEntry, Participant, Signer, TxEntry}
import xyz.bitml.api.persistence.{MetaStorage, ParticipantStorage, State, TxStorage}
import xyz.bitml.api.serialization.Serializer

import scala.concurrent.{Await, Future}

class Test_Oracle extends AnyFunSuite with BeforeAndAfterAll {

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

  test("Signature exchange and transaction assembly based on Balzac.Oracle"){

    val oracle_t_blank = Transaction.read("02000000017875f000ee77e9adac3224ad43c977b22b02f65339b7c69e1d7780f92e2e7fcb0000000023002102a6d35321c8930c1da17df79edebaf13192ee3e39c9abcea6d8dd9c5f3640e2abffffffff0100e1f5050000000017a91459f8b912203e01527f5feed3dfd6740773c8022d8700000000")
    val oracle_t1_2blank = Transaction.read("0200000001ad765fa02c697d04b393f012c6ea0b9dc471c60ca5832cc9622c591aecabc925000000005500004c516b6b006c766c766b7c6b522103859a0f601cf485a72ec097fddd798c694b0257f69f0229506f8ea923bc600c5e210237c53ebef2992c5b3f0efca8b849f4969095b31e597bdab292385bb132c30f3e52aeffffffff0100e1f505000000001976a914ba91ed34ad92a7c2aa2d764c73cd0f31a18df68088ac00000000")
    val txdb = new TxStorage()
    txdb.save("T", oracle_t_blank)
    txdb.save("T1", oracle_t1_2blank)

    val a_priv = PrivateKey.fromBase58("cSthBXr8YQAexpKeh22LB9PdextVE1UJeahmyns5LzcmMDSy59L4", Base58.Prefix.SecretKeyTestnet)._1
    val a_pub = a_priv.publicKey
    val alice_p = Participant("Alice", List(a_pub), Address("akka", "test", "127.0.0.1", 25000))
    val b_priv = PrivateKey.fromBase58("cQmSz3Tj3usor9byskhpCTfrmCM5cLetLU9Xw6y2csYhxSbKDzUn", Base58.Prefix.SecretKeyTestnet)._1
    val b_pub = b_priv.publicKey
    val bob_p = Participant("Bob", List(b_pub), Address("akka", "test", "127.0.0.1", 25001))
    val o_priv = PrivateKey.fromBase58("cTyxEAoUSKcC9NKFCjxKTaXzP8i1ufEKtwVVtY6AsRPpRgJTZQRt", Base58.Prefix.SecretKeyTestnet)._1
    val o_pub = o_priv.publicKey
    val oracle_p = Participant("Oracle", List(o_pub), Address("akka", "test", "127.0.0.1", 25002))
    val partdb = new ParticipantStorage()
    partdb.save(alice_p)
    partdb.save(bob_p)
    partdb.save(oracle_p)

    val t_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2PKH, chunkPrivacy= ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(a_pub), data = ByteVector.empty))
    val t1_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy= ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(b_pub), data = ByteVector.empty),
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy= ChunkPrivacy.PUBLIC, chunkIndex = 1, owner = Option(o_pub), data = ByteVector.empty))
    val t_entry = TxEntry(name = "T", indexData = Map(0 -> IndexEntry(amt = Btc(1).toSatoshi ,chunkData = t_chunks)))
    val t1_entry = TxEntry(name = "T1", indexData = Map(0 -> IndexEntry(amt = Btc(1).toSatoshi ,chunkData = t1_chunks)))

    val metadb = new MetaStorage()
    metadb.save(t_entry)
    metadb.save(t1_entry)

    val ser = new Serializer()
    val blankState = new Serializer(ChunkPrivacy.PRIVATE).prettyPrintState(State(partdb, txdb, metadb))
    // println(startingState)

    // Start 3 Client objects each with their participant's private key and the initial state json.

    //val testSystem = ActorSystem(name = "internalTestSystem")


    val alice = testSystem.actorOf(Props(classOf[Client]))
    val bob = testSystem.actorOf(Props(classOf[Client]))
    val oracle = testSystem.actorOf(Props(classOf[Client]))

    // Initializing the state will automatically convert the internal transactions into segwit.
    alice ! Init(jsonState = blankState, identity = a_priv)
    bob ! Init(jsonState = blankState, identity = b_priv)
    oracle ! Init(jsonState = blankState, identity = o_priv)

    // Start their network interfaces.
    alice ! Listen("test_application.conf", alice_p.endpoint.system)
    bob ! Listen("test_application_b.conf", bob_p.endpoint.system)
    oracle ! Listen("test_application_o.conf", oracle_p.endpoint.system)

    // Verify T's TxOut 0 and the matching TxIn has independently been "modernized" into a p2wsh by each participant.
    implicit val timeout : Timeout = Timeout(2000 milliseconds)
    val future = alice ? DumpState()
    val res = ser.loadState(Await.result((future), timeout.duration).asInstanceOf[CurrentState].state)
    // These have been correctly converted. Test_converter tests more on this separately.
    assert(res.metadb.fetch("T1").get.indexData(0).chunkData(0).chunkType == ChunkType.SIG_P2WSH)
    assert(res.metadb.fetch("T1").get.indexData(0).chunkData(1).chunkType == ChunkType.SIG_P2WSH)
    //assert(res.txdb.fetch("T").get.txOut(0).publicKeyScript.length == 34) // P2WSH: OP_0 :: PUSHDATA(32 byte sha256)
    // This should be left as is as we can't change the pubKeyScript associated.
    //assert(res.metadb.fetch("T").get.indexData(0).chunkData(0).chunkType == ChunkType.SIG_P2PKH)

    // Verify Alice can already assemble T on her own.
    // This creates a problem of its own: if the assembled tx is non-segwit,
    // the Signer can't update the txid referred by everyone else.
    // Alice will receive a warning and then update the references to T.
    //
    val future2 = alice ? TryAssemble("T")
    val res2 = Await.result(future2, timeout.duration).asInstanceOf[AssembledTx].serializedTx
    // The node has produced a transaction.
    assert(res2.length != 0)
    // The signature has been produced and placed.
    assert(Script.parse(Transaction.read(res2).txIn(0).signatureScript)(0) != Seq(OP_0)(0))

    // Alice has already started asking for signatures from the first TryAssemble.
    // alice ! AskForSigs("T1")

    // Let Bob and Oracle exchange signatures to each other when prompted.
    bob ! AskForSigs("T1")
    oracle ! AskForSigs("T1")
    Thread.sleep(3000) // this is completely non-deterministic. TODO: event driven state reporting? totally doable with akka.

    // Verify all 3 have all necessary info to assemble T1.
    for (participant <- Seq(alice, bob, oracle)){
      val future3 = participant ? TryAssemble("T1")
      val res3 = Await.result(future3, timeout.duration).asInstanceOf[AssembledTx].serializedTx
      // The node has produced a transaction.
      assert(res3.length != 0)
      // The signature has been produced and placed.
      assert(Transaction.read(res3).txIn(0).witness.stack(0) != ByteVector.empty)
      assert(Transaction.read(res3).txIn(0).witness.stack(1) != ByteVector.empty)
    }
    // debug: dump final state of each participant into json
    for (participant <- Seq(alice, bob, oracle)) {
      println(participant.path)
      val futState = participant ? DumpState()
      println(Await.result(futState, timeout.duration).asInstanceOf[CurrentState].state)

      participant ! StopListening()
    }
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
  }

  test("Oracle Timed Version - Withdraw Bob") {
    val t = Transaction.read("02000000017875f000ee77e9adac3224ad43c977b22b02f65339b7c69e1d7780f92e2e7fcb000000006a473044022078f74bc86bb9ff3872fa824eff9ac0bf325c5a137f0cff1c5812fd85676cb40002203a7d7a6315d7de5cd389d07c6ac67e315b3e1388a5ac2aa1066331828560b8b1012103ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c3ffffffff0100e1f5050000000017a91452155b181071f4df49dea8bc0aa1c048772a2fa98700000000")
    val alice_t1 = Transaction.read("02000000016379aa276cab7ebd2f7677d3eb73bf7178ee646a17b29cf85ff8a9133f83b5f300000000cd47304402200e6d64cc3b54f44f938e995889e2b6d863851cab1bc9915b611c386442639e93022057e0647875a9a81246fc8024b672ed903eaee93cae5a4b9ba79824e21b826a3901004c826b6b006c766c766b7c6b522103859a0f601cf485a72ec097fddd798c694b0257f69f0229506f8ea923bc600c5e210237c53ebef2992c5b3f0efca8b849f4969095b31e597bdab292385bb132c30f3e52ae63516704005c295cb1756c766b2103ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c3ac68feffffff0100e1f505000000001976a914448f9bd84d520fb00adb83f26d8a78ddc5403c8988ac005c295c")
    val bob_t1 = Transaction.read("02000000016379aa276cab7ebd2f7677d3eb73bf7178ee646a17b29cf85ff8a9133f83b5f300000000fd150147304402200e5f07c09238ae90c3a58661763bf67dee619b717b5bbf2137c73d5c01fb53cc022055325b86d3e4911280dac2c29a5ea990b65e1c39418b03ff83e11a804621c58201483045022100d294cf1b34a08e9e6573af8fb31b8c46dee370e88bc13172091fbcd7a62907d6022040aa1c435fec77285478dc2c89132da2ec1d4fefa5d2e784314411b7317b9d98014c826b6b006c766c766b7c6b522103859a0f601cf485a72ec097fddd798c694b0257f69f0229506f8ea923bc600c5e210237c53ebef2992c5b3f0efca8b849f4969095b31e597bdab292385bb132c30f3e52ae63516704005c295cb1756c766b2103ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c3ac68ffffffff0100e1f505000000001976a914ba91ed34ad92a7c2aa2d764c73cd0f31a18df68088ac00000000")

    val dbx = new TxStorage()
    dbx.save("T", t)
    dbx.save("T1_alice", alice_t1)
    dbx.save("T1_bob", bob_t1)

    val privA = PrivateKey.fromBase58("cSthBXr8YQAexpKeh22LB9PdextVE1UJeahmyns5LzcmMDSy59L4", Base58.Prefix.SecretKeyTestnet)._1
    val pubA = privA.publicKey
    val privB = PrivateKey.fromBase58("cQmSz3Tj3usor9byskhpCTfrmCM5cLetLU9Xw6y2csYhxSbKDzUn", Base58.Prefix.SecretKeyTestnet)._1
    val pubB = privB.publicKey
    val privO = PrivateKey.fromBase58("cTyxEAoUSKcC9NKFCjxKTaXzP8i1ufEKtwVVtY6AsRPpRgJTZQRt", Base58.Prefix.SecretKeyTestnet)._1
    val pubO = privO.publicKey

    val alice_p = Participant("Alice", List(pubA), Address("akka", "test", "127.0.0.1", 25000))
    val bob_p = Participant("Bob", List(pubB), Address("akka", "test", "127.0.0.1", 25001))
    val oracle_p = Participant("Oracle" , List(pubO), Address("akka", "test", "127.0.0.1", 25002))

    val partDb = new ParticipantStorage()
    partDb.save(alice_p)
    partDb.save(bob_p)
    partDb.save(oracle_p)

    val t_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2PKH, chunkPrivacy = ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(pubA), data = ByteVector.empty))

    val t1_bob_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy = ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(pubB), data = ByteVector.empty),
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy = ChunkPrivacy.PUBLIC, chunkIndex = 1, owner = Option(pubO), data = ByteVector.empty))

    val t1_alice_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy = ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(pubA), data = ByteVector.empty))

    val t_entry = TxEntry(name = "T", indexData = Map(0 -> IndexEntry(amt = Btc(1).toSatoshi ,chunkData = t_chunks)))
    val t1_bob_entry = TxEntry(name = "T1_bob", indexData = Map(0 -> IndexEntry(amt = Btc(1).toSatoshi ,chunkData = t1_bob_chunks)))
    val t1_alice_entry = TxEntry(name = "T1_alice", indexData = Map(0 -> IndexEntry(amt = Btc(1).toSatoshi, chunkData = t1_alice_chunks)))

    val metadb = new MetaStorage()
    metadb.save(t_entry)
    metadb.save(t1_bob_entry)
    metadb.save(t1_alice_entry)

    val ser = new Serializer()
    val startingState = ser.prettyPrintState(State(partDb, dbx, metadb))
    // println(startingState)


    val alice = testSystem.actorOf(Props(classOf[Client]))
    val bob = testSystem.actorOf(Props(classOf[Client]))
    val oracle = testSystem.actorOf(Props(classOf[Client]))

    // Initializing the state will automatically convert the internal transactions into segwit.
    alice ! Init(privA , startingState)
    bob ! Init(privB , startingState)
    oracle ! Init(privO, startingState)

    // Start their network interfaces.
    alice ! Listen("test_application.conf", alice_p.endpoint.system)
    bob ! Listen("test_application_b.conf", bob_p.endpoint.system)
    oracle ! Listen("test_application_o.conf", oracle_p.endpoint.system)

    implicit val timeout : Timeout = Timeout(2000 milliseconds)
    val future = alice ? DumpState()
    val res = ser.loadState(Await.result((future), timeout.duration).asInstanceOf[CurrentState].state)
    // These have been correctly converted. Test_converter tests more on this separately.
    assert(res.metadb.fetch("T1_bob").get.indexData(0).chunkData(0).chunkType == ChunkType.SIG_P2WSH)
    assert(res.metadb.fetch("T1_bob").get.indexData(0).chunkData(1).chunkType == ChunkType.SIG_P2WSH)
   // assert(res.txdb.fetch("T").get.txOut(0).publicKeyScript.length == 34) // P2WSH: OP_0 :: PUSHDATA(32 byte sha256)
    // This should be left as is as we can't change the pubKeyScript associated.
    //assert(res.metadb.fetch("T").get.indexData(0).chunkData(0).chunkType == ChunkType.SIG_P2PKH)

    val future2 = alice ? TryAssemble("T")
    val res2 = Await.result(future2, timeout.duration).asInstanceOf[AssembledTx].serializedTx
    // The node has produced a transaction.
    assert(res2.length != 0)
    // The signature has been produced and placed.
    assert(Script.parse(Transaction.read(res2).txIn(0).signatureScript)(0) != Seq(OP_0)(0))

    // Alice has already started asking for signatures from the first TryAssemble.
    // alice ! AskForSigs("T1")

    // Let Bob and Oracle exchange signatures to each other when prompted.
    bob ! AskForSigs("T1_bob")
    oracle ! AskForSigs("T1_bob")
    Thread.sleep(3000) // this is completely non-deterministic. TODO: event driven state reporting? totally doable with akka.

    // Verify all 3 have all necessary info to assemble T1.
    for (participant <- Seq(alice, bob, oracle)){
      val future3 = participant ? TryAssemble("T1_bob")
      val res3 = Await.result(future3, timeout.duration).asInstanceOf[AssembledTx].serializedTx
      // The node has produced a transaction.
      assert(res3.length != 0)
      // The signature has been produced and placed.
      assert(Transaction.read(res3).txIn(0).witness.stack(0) != ByteVector.empty)
      assert(Transaction.read(res3).txIn(0).witness.stack(1) != ByteVector.empty)
    }
    // debug: dump final state of each participant into json
    for (participant <- Seq(alice, bob, oracle)) {
      println(participant.path)
      val futState = participant ? DumpState()
      println(Await.result(futState, timeout.duration).asInstanceOf[CurrentState].state)

      participant ! StopListening()
    }
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
  }

  test("Oracle Timed Version - Withdraw Alice") {
    val t = Transaction.read("02000000017875f000ee77e9adac3224ad43c977b22b02f65339b7c69e1d7780f92e2e7fcb000000006a473044022078f74bc86bb9ff3872fa824eff9ac0bf325c5a137f0cff1c5812fd85676cb40002203a7d7a6315d7de5cd389d07c6ac67e315b3e1388a5ac2aa1066331828560b8b1012103ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c3ffffffff0100e1f5050000000017a91452155b181071f4df49dea8bc0aa1c048772a2fa98700000000")
    val alice_t1 = Transaction.read("02000000016379aa276cab7ebd2f7677d3eb73bf7178ee646a17b29cf85ff8a9133f83b5f300000000cd47304402200e6d64cc3b54f44f938e995889e2b6d863851cab1bc9915b611c386442639e93022057e0647875a9a81246fc8024b672ed903eaee93cae5a4b9ba79824e21b826a3901004c826b6b006c766c766b7c6b522103859a0f601cf485a72ec097fddd798c694b0257f69f0229506f8ea923bc600c5e210237c53ebef2992c5b3f0efca8b849f4969095b31e597bdab292385bb132c30f3e52ae63516704005c295cb1756c766b2103ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c3ac68feffffff0100e1f505000000001976a914448f9bd84d520fb00adb83f26d8a78ddc5403c8988ac005c295c")
    val bob_t1 = Transaction.read("02000000016379aa276cab7ebd2f7677d3eb73bf7178ee646a17b29cf85ff8a9133f83b5f300000000fd150147304402200e5f07c09238ae90c3a58661763bf67dee619b717b5bbf2137c73d5c01fb53cc022055325b86d3e4911280dac2c29a5ea990b65e1c39418b03ff83e11a804621c58201483045022100d294cf1b34a08e9e6573af8fb31b8c46dee370e88bc13172091fbcd7a62907d6022040aa1c435fec77285478dc2c89132da2ec1d4fefa5d2e784314411b7317b9d98014c826b6b006c766c766b7c6b522103859a0f601cf485a72ec097fddd798c694b0257f69f0229506f8ea923bc600c5e210237c53ebef2992c5b3f0efca8b849f4969095b31e597bdab292385bb132c30f3e52ae63516704005c295cb1756c766b2103ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c3ac68ffffffff0100e1f505000000001976a914ba91ed34ad92a7c2aa2d764c73cd0f31a18df68088ac00000000")

    val dbx = new TxStorage()
    dbx.save("T", t)
    dbx.save("T1_alice", alice_t1)
    dbx.save("T1_bob", bob_t1)

    val privA = PrivateKey.fromBase58("cSthBXr8YQAexpKeh22LB9PdextVE1UJeahmyns5LzcmMDSy59L4", Base58.Prefix.SecretKeyTestnet)._1
    val pubA = privA.publicKey
    val privB = PrivateKey.fromBase58("cQmSz3Tj3usor9byskhpCTfrmCM5cLetLU9Xw6y2csYhxSbKDzUn", Base58.Prefix.SecretKeyTestnet)._1
    val pubB = privB.publicKey
    val privO = PrivateKey.fromBase58("cTyxEAoUSKcC9NKFCjxKTaXzP8i1ufEKtwVVtY6AsRPpRgJTZQRt", Base58.Prefix.SecretKeyTestnet)._1
    val pubO = privO.publicKey

    val alice_p = Participant("Alice", List(pubA), Address("akka", "test", "127.0.0.1", 25000))
    val bob_p = Participant("Bob", List(pubB), Address("akka", "test", "127.0.0.1", 25001))
    val oracle_p = Participant("Oracle" , List(pubO), Address("akka", "test", "127.0.0.1", 25002))

    val partDb = new ParticipantStorage()
    partDb.save(alice_p)
    partDb.save(bob_p)
    partDb.save(oracle_p)

    val t_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2PKH, chunkPrivacy = ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(pubA), data = ByteVector.empty))

    val t1_bob_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy = ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(pubB), data = ByteVector.empty),
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy = ChunkPrivacy.PUBLIC, chunkIndex = 1, owner = Option(pubO), data = ByteVector.empty))

    val t1_alice_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy = ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(pubA), data = ByteVector.empty))

    val t_entry = TxEntry(name = "T", indexData = Map(0 -> IndexEntry(amt = Btc(1).toSatoshi ,chunkData = t_chunks)))
    val t1_bob_entry = TxEntry(name = "T1_bob", indexData = Map(0 -> IndexEntry(amt = Btc(1).toSatoshi ,chunkData = t1_bob_chunks)))
    val t1_alice_entry = TxEntry(name = "T1_alice", indexData = Map(0 -> IndexEntry(amt = Btc(1).toSatoshi, chunkData = t1_alice_chunks)))

    val metadb = new MetaStorage()
    metadb.save(t_entry)
    metadb.save(t1_bob_entry)
    metadb.save(t1_alice_entry)

    val ser = new Serializer()
    val startingState = ser.prettyPrintState(State(partDb, dbx, metadb))
    // println(startingState)


    val alice = testSystem.actorOf(Props(classOf[Client]))
    val bob = testSystem.actorOf(Props(classOf[Client]))
    val oracle = testSystem.actorOf(Props(classOf[Client]))

    // Initializing the state will automatically convert the internal transactions into segwit.
    alice ! Init(privA , startingState)
    bob ! Init(privB , startingState)
    oracle ! Init(privO, startingState)

    // Start their network interfaces.
    alice ! Listen("test_application.conf", alice_p.endpoint.system)
    bob ! Listen("test_application_b.conf", bob_p.endpoint.system)
    oracle ! Listen("test_application_o.conf", oracle_p.endpoint.system)

    implicit val timeout : Timeout = Timeout(2000 milliseconds)
    val future = alice ? DumpState()
    val res = ser.loadState(Await.result((future), timeout.duration).asInstanceOf[CurrentState].state)
    // These have been correctly converted. Test_converter tests more on this separately.
    assert(res.metadb.fetch("T1_bob").get.indexData(0).chunkData(0).chunkType == ChunkType.SIG_P2WSH)
    assert(res.metadb.fetch("T1_bob").get.indexData(0).chunkData(1).chunkType == ChunkType.SIG_P2WSH)
    // assert(res.txdb.fetch("T").get.txOut(0).publicKeyScript.length == 34) // P2WSH: OP_0 :: PUSHDATA(32 byte sha256)
    // This should be left as is as we can't change the pubKeyScript associated.
    //assert(res.metadb.fetch("T").get.indexData(0).chunkData(0).chunkType == ChunkType.SIG_P2PKH)

    val future2 = alice ? TryAssemble("T")
    val res2 = Await.result(future2, timeout.duration).asInstanceOf[AssembledTx].serializedTx
    // The node has produced a transaction.
    assert(res2.length != 0)
    // The signature has been produced and placed.
    assert(Script.parse(Transaction.read(res2).txIn(0).signatureScript)(0) != Seq(OP_0)(0))

    val future3 = alice ? TryAssemble("T1_alice")
    val res3 = Await.result(future3, timeout.duration).asInstanceOf[AssembledTx].serializedTx
    // debug: dump final state of each participant into json
    for (participant <- Seq(alice, bob, oracle)) {
      println(participant.path)
      val futState = participant ? DumpState()
      println(Await.result(futState, timeout.duration).asInstanceOf[CurrentState].state)

      participant ! StopListening()
    }
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
  }
  /*test("Oracle") {

    // Generate initial state JSON with our own internal serialization
    val tinit = Transaction.read("02000000027be5fa01cf6465d71c0335c5f34c53a1d2a7b29d567d442e1e98f0e64e15a06a0000000023002102a6d35321c8930c1da17df79edebaf13192ee3e39c9abcea6d8dd9c5f3640e2abffffffff9d26302a3e82b2475a6ba09a0e0e0c6cf4626125ff012232180a41415be1e5a00100000023002102a6d35321c8930c1da17df79edebaf13192ee3e39c9abcea6d8dd9c5f3640e2abffffffff01b15310000000000017a9147a06737efe61a6d916abdc59b7c099ae570c39ca8700000000")
    println(tinit.txIn+"\n"+tinit.txOut)
    val t1 = Transaction.read("020000000164206af5b72934c9b198dce52f65e2a1e3d87110ebe458ddd87e048c0d7b60d4000000005500004c516b6b006c766c766b7c6b522103859a0f601cf485a72ec097fddd798c694b0257f69f0229506f8ea923bc600c5e2103ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c352aeffffffff0180de0f00000000001976a914ce07ee1448bbb80b38ae0c03b6cdeff40ff326ba88ac00000000")
    println(t1.txIn+"\n"+t1.txOut)
    val txdb = new TxStorage()
    txdb.save("Tinit", tinit)
    txdb.save("T1", t1)

    val a_priv = PrivateKey.fromBase58("cSthBXr8YQAexpKeh22LB9PdextVE1UJeahmyns5LzcmMDSy59L4", Base58.Prefix.SecretKeyTestnet)._1
    val a_pub = a_priv.publicKey
    val a_p = Participant("A", List(a_pub), Address("akka", "test", "127.0.0.1", 25000))

    val b_priv = PrivateKey.fromBase58("cQmSz3Tj3usor9byskhpCTfrmCM5cLetLU9Xw6y2csYhxSbKDzUn", Base58.Prefix.SecretKeyTestnet)._1
    val b_pub = b_priv.publicKey
    val b_p = Participant("B", List(b_pub), Address("akka", "test", "127.0.0.1", 25001))

    val o_priv = PrivateKey.fromBase58("cTyxEAoUSKcC9NKFCjxKTaXzP8i1ufEKtwVVtY6AsRPpRgJTZQRt", Base58.Prefix.SecretKeyTestnet)._1
    val o_pub = o_priv.publicKey
    val o_p = Participant("O", List(o_pub), Address("akka", "test", "127.0.0.1", 25002))

    val partdb = new ParticipantStorage()

    partdb.save(a_p)
    partdb.save(b_p)
    partdb.save(o_p)

    val tinit0_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2PKH, chunkPrivacy = ChunkPrivacy.AUTH, chunkIndex = 0, owner = Option(a_pub), data = ByteVector.empty))
    val tinit1_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2PKH, chunkPrivacy = ChunkPrivacy.AUTH, chunkIndex = 0, owner = Option(a_pub), data = ByteVector.empty))
    val t1_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy = ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(b_pub), data = ByteVector.empty),
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy = ChunkPrivacy.PUBLIC, chunkIndex = 1, owner = Option(a_pub), data = ByteVector.empty))
    val tinit_entry = TxEntry(name = "Tinit", indexData = Map(
      0 -> IndexEntry(amt = Satoshi(1000000), chunkData = tinit0_chunks),
      1 -> IndexEntry(amt = Satoshi(100000), chunkData = tinit1_chunks)))
    val t1_entry = TxEntry(name = "T1", indexData = Map(0 -> IndexEntry(amt = Satoshi(1070001), chunkData = t1_chunks)))

    val metadb = new MetaStorage()
    metadb.save(tinit_entry)
    metadb.save(t1_entry)

    val initialState = State(partdb, txdb, metadb)
    val stateJson = new Serializer().prettyPrintState(initialState)

    val alice = testSystem.actorOf(Props[Client])
    val bob = testSystem.actorOf(Props[Client])
    val oracle = testSystem.actorOf(Props[Client])

    alice ! Init(a_priv, stateJson)
    bob ! Init (b_priv, stateJson)
    oracle ! Init (o_priv, stateJson)

    alice ! Listen("test_application.conf", a_p.endpoint.system)
    bob ! Listen("test_application.conf", b_p.endpoint.system)
    oracle ! Listen("test_application.conf", o_p.endpoint.system)

    // AFter letting A initialize, see if it can assemble Tinit on its own
    Thread.sleep(2000)
    implicit val timeout: Timeout = 1 second
    val future3 = alice ? TryAssemble("Tinit")
    val res3 = Await.result(future3, timeout.duration).asInstanceOf[AssembledTx].serializedTx
    // The node has produced a transaction.
    assert(res3.length != 0)
    println(Transaction.read(res3).txIn)

    alice ! StopListening()
    bob ! StopListening()
    oracle ! StopListening()
  }*/
}
