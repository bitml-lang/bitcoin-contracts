

import scala.concurrent.duration._
import akka.actor.{ActorSystem, Address, CoordinatedShutdown, Props}
import akka.pattern.ask
import akka.util.Timeout
import fr.acinq.bitcoin.Crypto.{PrivateKey, PublicKey}
import fr.acinq.bitcoin.{Base58, Btc, ByteVector32, OP_0, OutPoint, Satoshi, Script, ScriptElt, ScriptFlags, Transaction, TxIn, TxOut}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import scodec.bits.ByteVector
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient.RawTransaction
import xyz.bitml.api.messaging.{AskForSigs, AssembledTx, CurrentState, DumpState, Init, Internal, Listen, Ping, Pong, PreInit, SearchTx, StopListening, TryAssemble}
import xyz.bitml.api.{ChunkEntry, ChunkPrivacy, ChunkType, Client, IndexEntry, Participant, Signer, TxEntry}
import xyz.bitml.api.persistence.{MetaStorage, ParticipantStorage, State, TxStorage}
import xyz.bitml.api.serialization.Serializer
import wf.bitcoin.javabitcoindrpcclient.{BitcoinJSONRPCClient, BitcoinRPCException}

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


  test("prova pubblicazione") {
    val alice_addr = "bcrt1qpvya0fx4a3rny37jt8twqe4kt95qwcvagvn7ed"
    val bob_addr = "bcrt1q6hhz23me8gqumprwzdrm4yrcy9yntp7l53nzt6"
    val oracle_addr = "bcrt1qp2g8a0ele0ywv9tc9ywutv58j0prhqznxvltn0"
    val privA = "cVbFzgZSpnuKvNT5Z3DofF9dV4Dr1zFQJw9apGZDVaG73ULqM7XS"
    val privB = "cPU3AmQFsBxvrBgTWc1j3pS6T7m4bYWMFQyPnR9Qp3o3UTCBwspZ"
    val privO = "cQAEMfAQwbVDSUDT3snYu9QVfbdBTVMrm36zoArizBkAaPYTtLdH"
    val rpc = new BitcoinJSONRPCClient()
    println(rpc.getBalance())
    println(rpc.getReceivedByAddress("bcrt1qpvya0fx4a3rny37jt8twqe4kt95qwcvagvn7ed"))
    println(rpc.getReceivedByAddress("bcrt1q6hhz23me8gqumprwzdrm4yrcy9yntp7l53nzt6"))
    println(rpc.getReceivedByAddress("bcrt1qp2g8a0ele0ywv9tc9ywutv58j0prhqznxvltn0"))
    //tx id = 9507a19dbf0e53787480779df2a123192485e69aeef7fc46f6d557c04e4829ec
    //tx id hash = ec29484ec057d5f646fcf7ee9ae685241923a1f29d77807478530ebf9da10795
    //d0817628c3b16e45e638de38b39a509f090f1ab205e18c7f666135214f91859a
    //9a85914f213561667f8ce105b21a0f099f509ab338de38e6456eb1c3287681d0

    val t = Transaction.read("020000000106bef082338220da8bf782f5ab60a1e4fa645a0dc80ca5c5c0001c4f85a21879000000006b483045022100adeba2f2637bf86d6084175bb41de8352147521fd13d40463afb701e883a6482022070f2b6dcfd3f6d36371139f0a0f56f70cd7781c18798315139129f8bdf60a04b0121032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32effffffff0100e1f5050000000017a914f63274315fd47e310c42022791cd51e35a550a248700000000")
    val tx1 = Transaction(
      version = 2L,
      txIn = List(
        TxIn(OutPoint(ByteVector32.fromValidHex("9a85914f213561667f8ce105b21a0f099f509ab338de38e6456eb1c3287681d0"), 1), signatureScript = t.txIn(0).signatureScript, sequence = 0xFFFFFFFFL)
      ),
      txOut = t.txOut,
      lockTime = 0L
    )
    println(tx1.txIn(0).outPoint)
    rpc.sendRawTransaction(tx1.toString())
  }

  test("Signature exchange and transaction assembly based on Balzac.Oracle"){
    //address Alice = bcrt1qes4dp4wquajt6ah586ljvc9hd4628uuphehgjj
    //privAlice = cUXce18sTYh3qATjspyYyimmjRARxaHZuecm5GcFYbR4D8r8w64C
    //address Bob = bcrt1qvh68d0eahy946dzrvmjca0g4d44hz24a7pajew
    //privBob = cV65jV2ejbJsiuTaGQsw5TZ3d8Me16xMRDot7kc2xkSGz6D7X3Hh
    //address Oracle = bcrt1q9ajhmv5dvc0nyecynkrpyh25q793yz5xcrvqya
    //privOracle = cR78nCSzT7bRUE5YjTZBXFftrFQPftaKqveg9rsQst1zkoN31HWr

    val oracle_t_blank = Transaction.read("02000000018d92c85e727be72889c37a87af8fc55c8134fee0411967a82ee37f2a7dcc53af000000006a473044022065d93de05ac31265ab4bd4b5bc9f03fbd415d9b27c48f1c4a2f8cd441e6ab1a302201244007d92b4c1d23ee93dd8992d4ced3ebf878325b061b80156e5a2693d6fd601210291ac7f33209311d9bb744c77633b9606e5b7962d5ea4917f004431d83d923aefffffffff0100e1f5050000000017a9148b44fc63e3af60267007d48ab865f1159321f34a8700000000")
    val oracle_t1_2blank = Transaction.read("0200000001d0bc23b5334ed721dae9e10d9fca7ba3c365b6ecda9313df4b48643421cfefaf00000000e4483045022100e79386fa58b1992ed871b79da318750ed9e51bde839902d937d6ea3585d6b263022031eb8b89f759d44d98c3cce7a05c94a4a43c94c254c79c065e288f367ab6b8a401473044022032151a0977c5615cb996924b11729e11819ce5a85328c152783eb0c6cce733390220174db7caf6f98e3121daa3f04897a60285b83ae01b909320e97730e80f572a8a014c516b6b006c766c766b7c6b5221038d177f7fe1d4c9f411692bf8c4ddd4265143ed954b66de2220d7f28594f3bf1021028f99b126c6505e549e8ae0a337456870295a49056463984bcaeba18d1bb197e352aeffffffff0100e1f505000000001976a91465f476bf3db90b5d344366e58ebd156d6b712abd88ac00000000")
    val txdb = new TxStorage()
    txdb.save("T", oracle_t_blank)
    txdb.save("T1", oracle_t1_2blank)

    val a_priv = PrivateKey.fromBase58("cUXce18sTYh3qATjspyYyimmjRARxaHZuecm5GcFYbR4D8r8w64C", Base58.Prefix.SecretKeyTestnet)._1
    val a_pub = a_priv.publicKey
    val alice_p = Participant("Alice", List(a_pub), Address("akka", "test", "127.0.0.1", 25000))
    val b_priv = PrivateKey.fromBase58("cV65jV2ejbJsiuTaGQsw5TZ3d8Me16xMRDot7kc2xkSGz6D7X3Hh", Base58.Prefix.SecretKeyTestnet)._1
    val b_pub = b_priv.publicKey
    val bob_p = Participant("Bob", List(b_pub), Address("akka", "test", "127.0.0.1", 25001))
    val o_priv = PrivateKey.fromBase58("cR78nCSzT7bRUE5YjTZBXFftrFQPftaKqveg9rsQst1zkoN31HWr", Base58.Prefix.SecretKeyTestnet)._1
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
    val future2 = alice ? TryAssemble("T", autoPublish = true)
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
    val t_old = Transaction.read("02000000017875f000ee77e9adac3224ad43c977b22b02f65339b7c69e1d7780f92e2e7fcb000000006a473044022078f74bc86bb9ff3872fa824eff9ac0bf325c5a137f0cff1c5812fd85676cb40002203a7d7a6315d7de5cd389d07c6ac67e315b3e1388a5ac2aa1066331828560b8b1012103ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c3ffffffff0100e1f5050000000017a91452155b181071f4df49dea8bc0aa1c048772a2fa98700000000")
    val t = Transaction(
      version = 2L,
      txIn = List(
        TxIn(OutPoint(ByteVector32.fromValidHex("9a85914f213561667f8ce105b21a0f099f509ab338de38e6456eb1c3287681d0"), 1), signatureScript = t_old.txIn(0).signatureScript, sequence = 0xFFFFFFFFL)
      ),
      txOut = t_old.txOut,
      lockTime = 0L
    )
    val alice_t1 = Transaction.read("0200000001e7f594a922e5f66c9c6a0467415c341a7838c6590627ecc855384025edf87d0e00000000ce48304502210084e38b078050ed0d18db22f44c91e75e9dd4f3d455808680cca233df6afd81c4022041eb10a617c39b6f0b62ae6be5e8f6994580a179e9f30c9dd1b353df9e11db5c01004c826b6b006c766c766b7c6b5221038d177f7fe1d4c9f411692bf8c4ddd4265143ed954b66de2220d7f28594f3bf1021028f99b126c6505e549e8ae0a337456870295a49056463984bcaeba18d1bb197e352ae63516704005c295cb1756c766b210291ac7f33209311d9bb744c77633b9606e5b7962d5ea4917f004431d83d923aefac68feffffff0100e1f505000000001976a914cc2ad0d5c0e764bd76f43ebf2660b76d74a3f38188ac005c295c")
    val bob_t1 = Transaction.read("0200000001e7f594a922e5f66c9c6a0467415c341a7838c6590627ecc855384025edf87d0e00000000fd14014730440220778e10f881f921461fb693139efb75e1ab30dac6fe36cf4d7c6fd3774790c1fb022058563b87de37cc51bb4c1f6bd7117e640b1170c75d88e9a4657f0a84367524c801473044022045a151b1b6be24cc04a3569fee9eb74232b353879552d64ee6042f611217477c022049eddd7cd129a2f6b00aff5d9a56be1fad74a97f1e414313abe75cecac89e671014c826b6b006c766c766b7c6b5221038d177f7fe1d4c9f411692bf8c4ddd4265143ed954b66de2220d7f28594f3bf1021028f99b126c6505e549e8ae0a337456870295a49056463984bcaeba18d1bb197e352ae63516704005c295cb1756c766b210291ac7f33209311d9bb744c77633b9606e5b7962d5ea4917f004431d83d923aefac68ffffffff0100e1f505000000001976a91465f476bf3db90b5d344366e58ebd156d6b712abd88ac00000000")

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

  test("Oracle - Withdraw Bob with regtest publish") {
    val alice_addr = "bcrt1qpvya0fx4a3rny37jt8twqe4kt95qwcvagvn7ed"
    val bob_addr = "bcrt1q6hhz23me8gqumprwzdrm4yrcy9yntp7l53nzt6"
    val oracle_addr = "bcrt1qp2g8a0ele0ywv9tc9ywutv58j0prhqznxvltn0"
    //val privA = "cVbFzgZSpnuKvNT5Z3DofF9dV4Dr1zFQJw9apGZDVaG73ULqM7XS"
    //val privB = "cPU3AmQFsBxvrBgTWc1j3pS6T7m4bYWMFQyPnR9Qp3o3UTCBwspZ"
    //val privO = "cQAEMfAQwbVDSUDT3snYu9QVfbdBTVMrm36zoArizBkAaPYTtLdH"
    //tx id = 8127b3abdbede565d3808d0b0fae65edd457cb2e29403b03c49821730d75e4f4
    //tx hash = f4e4750d732198c4033b40292ecb57d4ed65ae0f0b8d80d365e5eddbabb32781
    //0200000001f4e4750d732198c4033b40292ecb57d4ed65ae0f0b8d80d365e5eddbabb327810100000000ffffffff0100e1f5050000000017a914c1ac75cca0be5b6c924abc68c056d91f78b58bad8700000000
    val t = Transaction.read("0200000001f4e4750d732198c4033b40292ecb57d4ed65ae0f0b8d80d365e5eddbabb32781010000006b483045022100b77139c8a1a0ed1c27a7018ab045dbabf7c1935d137ff76b47b60b83b0ea2b6c0220756b08797597cca0a9b316d094a5e07c82c258138777b80d49e069f4f556790e012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93affffffff0100e1f5050000000017a914c1ac75cca0be5b6c924abc68c056d91f78b58bad8700000000")
    /*val t = Transaction(
      version = 2L,
      txIn = List(
        TxIn(OutPoint(ByteVector32.fromValidHex("f4e4750d732198c4033b40292ecb57d4ed65ae0f0b8d80d365e5eddbabb32781"), 1), signatureScript = ByteVector.empty, sequence = 0xFFFFFFFFL)
      ),
      txOut = t_old.txOut,
      lockTime = 0L
    )*/
    println(t.toString())
    val alice_t1 = Transaction.read("02000000010f7d9f68ba5ff452433117ac3c2fb3b972e589b451d7133bd3bc97ddf6de014300000000cd473044022079150634dd0b6e3db945794be2583c228acfab9f5ac4aa0d09e22bc807a0c1c90220130e45ad7c33aad1379319ad3c23fd3b358065807b7152ae64c1d72a105eb1e401004c826b6b006c766c766b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e52ae63516704005c295cb1756c766b2103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93aac68feffffff0100e1f505000000001976a9140b09d7a4d5ec473247d259d6e066b6596807619d88ac005c295c")
    val bob_t1 = Transaction.read("02000000010f7d9f68ba5ff452433117ac3c2fb3b972e589b451d7133bd3bc97ddf6de014300000000fd1501483045022100cb65fc7739e51c1f9c846ef9af05e67f8761778b151850c0523e3dd1924ebb9f02202c25ba1070349e9725556c5ee9deb1edf6d79a895caa53eac2d61bb6109356ea0147304402204b49894b797d850fb1f7df6b0e4fd44e8e40b270dd3291f3d660264a7e60b43302202bae9b6754b30001df211af648e49e44970b6d460c451b6f3334cac315c49b36014c826b6b006c766c766b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e52ae63516704005c295cb1756c766b2103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93aac68ffffffff0100e1f505000000001976a914d5ee2547793a01cd846e1347ba907821493587df88ac00000000")

    //creating a transaction storage in which we put the transactions for later use of the partecipants
    val dbx = new TxStorage()
    dbx.save("T", t)
    dbx.save("T1_alice", alice_t1)
    dbx.save("T1_bob", bob_t1)

    //declaring the private and public keys for each partecipant
    val privA = PrivateKey.fromBase58("cVbFzgZSpnuKvNT5Z3DofF9dV4Dr1zFQJw9apGZDVaG73ULqM7XS", Base58.Prefix.SecretKeyTestnet)._1
    val pubA = privA.publicKey
    val privB = PrivateKey.fromBase58("cPU3AmQFsBxvrBgTWc1j3pS6T7m4bYWMFQyPnR9Qp3o3UTCBwspZ", Base58.Prefix.SecretKeyTestnet)._1
    val pubB = privB.publicKey
    val privO = PrivateKey.fromBase58("cQAEMfAQwbVDSUDT3snYu9QVfbdBTVMrm36zoArizBkAaPYTtLdH", Base58.Prefix.SecretKeyTestnet)._1
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
    val future2 = alice ? TryAssemble("T", autoPublish = true)
    //val tx = alice ! SearchTx("T")
    //println(tx)
    val res2 = Await.result(future2, timeout.duration).asInstanceOf[AssembledTx].serializedTx
    val tx = Transaction.read(res2)
    Thread.sleep(2000)
    alice ! SearchTx("T")
    Thread.sleep(2000)


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
