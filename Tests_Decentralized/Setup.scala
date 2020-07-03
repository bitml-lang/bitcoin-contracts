package Tests_Decentralized

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

import scala.concurrent.duration._
import scala.concurrent.Await

object Setup {
  def setup(): State = {
    //declare the serialized transactions that we need
    val t = Transaction.read("02000000000101fab973569613b81be2b4084b4dc29c5b54853d5ea062c6d674944bd75668e49f0100000000ffffffff0100e1f505000000002200203a5fcea85c2dca4480bd79f80bff55356b4779c6b5ef870a6c8086c1285e932f02473044022073a2cdbd8912bf01cdb20b0856707cf07d353890e8f84c00b1cac9793b3db97c02201d8a128279ad8f21592109b9c7477f0f26503a0e3a60375289bc0e32e3d36ebf012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a00000000")
    val t1 = Transaction.read("02000000000101349f51b00dce1f8068ec32bf9ea85f384d6f1f2d42350e7f13d375ff9fa94bdd0000000000ffffffff01605af40500000000160014d5ee2547793a01cd846e1347ba907821493587df04004730440220699557a9c9e7e62cfb4278cfb17570d23879fa0430847b2f3564ade1fbfe9b6402201a98c7195ab4f0252842068ab2b4c9e09caaf4760d659f72f4273fa7432583a901483045022100dd9a0a1370a32e17359f4ea567f7ddddb9433af573d85b2cd9b44f944d2f8f790220066c4539a07968e367aa3b1a7959e323f518f82c45a378904fd698149b93ff8901475221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e52ae00000000")


    //val a_priv = PrivateKey.fromBase58("cVbFzgZSpnuKvNT5Z3DofF9dV4Dr1zFQJw9apGZDVaG73ULqM7XS", Base58.Prefix.SecretKeyTestnet)._1
    //val b_priv = PrivateKey.fromBase58("cPU3AmQFsBxvrBgTWc1j3pS6T7m4bYWMFQyPnR9Qp3o3UTCBwspZ", Base58.Prefix.SecretKeyTestnet)._1
    //val o_priv = PrivateKey.fromBase58("cQAEMfAQwbVDSUDT3snYu9QVfbdBTVMrm36zoArizBkAaPYTtLdH", Base58.Prefix.SecretKeyTestnet)._1

    //public keys
    val a_pub = PublicKey(ByteVector.fromValidHex("03fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a"))
    val b_pub = PublicKey(ByteVector.fromValidHex("028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7"))
    val o_pub = PublicKey(ByteVector.fromValidHex("032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e"))

    //save the transactions
    val dbx = new TxStorage()
    dbx.save("T", t)
    dbx.save("T1", t1)

    //declare the partecipants along with their name, public keys and endpoints
    val alice_p = Participant("Alice", List(a_pub), Address("akka", "test", "127.0.0.1", 25000))
    val bob_p = Participant("Bob", List(b_pub), Address("akka", "test", "127.0.0.1", 25001))
    val oracle_p = Participant("Oracle", List(o_pub), Address("akka", "test", "127.0.0.1", 25002))

    //saving the partecipants on a storage
    val partDb = new ParticipantStorage()
    partDb.save(alice_p)
    partDb.save(bob_p)
    partDb.save(oracle_p)

    val t_chunks = Seq()
    //val t_chunks = Seq(
    //  ChunkEntry(chunkType = ChunkType.SIG_P2WPKH, chunkPrivacy= ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(a_pub), data = ByteVector.empty))
    val t1_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2WSH, chunkPrivacy= ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(b_pub), data = ByteVector.empty),
      ChunkEntry(chunkType = ChunkType.SIG_P2WSH, chunkPrivacy= ChunkPrivacy.PUBLIC, chunkIndex = 1, owner = Option(o_pub), data = ByteVector.empty))
    val t_entry = TxEntry(name = "T", indexData = Map(0 -> IndexEntry(amt = Btc(1).toSatoshi ,chunkData = t_chunks)))
    val t1_entry = TxEntry(name = "T1", indexData = Map(0 -> IndexEntry(amt = Btc(1).toSatoshi ,chunkData = t1_chunks)))

    //save the transactions meta on a storage
    val metadb = new MetaStorage()
    metadb.save(t_entry)
    metadb.save(t1_entry)

    //make the initial state and return it
    val initialState = State(partDb, dbx, metadb)
    initialState
  }
}
