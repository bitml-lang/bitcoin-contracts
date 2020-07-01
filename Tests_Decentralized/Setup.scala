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
    val funds = Transaction.read("02000000000101f4e4750d732198c4033b40292ecb57d4ed65ae0f0b8d80d365e5eddbabb327810000000000feffffff0207d3991700000000160014bb2ed9a85b49214921dee5c72edd3b5be100692f30f2f505000000001600140b09d7a4d5ec473247d259d6e066b6596807619d02473044022050c6d20d83d6f18ffba9688e26e4c58e798969379fca781fdc3ba4ff3a942388022004e0eab4ebfe56ff602580889224fbd55eccf7a0300b27b2a7f8eb585b634075012102c7f3e8008fabfa90c7f3ce21cc405da1891d7123e35edf4278d943a46fd1c1f400000000")
    val t = Transaction.read("0200000000010192572533e13518d6f63ebfbabdece591aadda200b948a20346be4c460a2529770100000000ffffffff0100e1f505000000002200203a5fcea85c2dca4480bd79f80bff55356b4779c6b5ef870a6c8086c1285e932f02483045022100d2d252f3dc608c4339e04592d42be42b40a1f5612b74d184bc3c0e97ebeb06920220485fe94b1070f911ae6e44d108b244cea303247e83f25e84d06c959ab29c1c35012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a00000000")
    val t1 = Transaction.read("02000000000101e75b0754f07087a00e0756dd332afd5a5577fe8c746de0f01e1f4b3643f5eebc0000000000ffffffff01605af40500000000160014d5ee2547793a01cd846e1347ba907821493587df0400473044022056212dc3db8f2081b689bda624a0ea066d7a6e9a39739c0ea72d59dc1f79acf8022003abfc7fd8e17facc895cb564c8f4ebd618cfb355e46b0fc8f381dc8c210e31101483045022100bcc980e807c5454527315e04d056e6a3ea2e92ab3708d69f501aa778ff851fa5022062afc554a02f987ae608bb0564e546cd79d0f295ee81ac631a3a739f158f86ba01475221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e52ae00000000")


    val a_priv = PrivateKey.fromBase58("cVbFzgZSpnuKvNT5Z3DofF9dV4Dr1zFQJw9apGZDVaG73ULqM7XS", Base58.Prefix.SecretKeyTestnet)._1
    val b_priv = PrivateKey.fromBase58("cPU3AmQFsBxvrBgTWc1j3pS6T7m4bYWMFQyPnR9Qp3o3UTCBwspZ", Base58.Prefix.SecretKeyTestnet)._1
    val o_priv = PrivateKey.fromBase58("cQAEMfAQwbVDSUDT3snYu9QVfbdBTVMrm36zoArizBkAaPYTtLdH", Base58.Prefix.SecretKeyTestnet)._1

    val a_pub = PublicKey(ByteVector.fromValidHex("03fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a"))
    val b_pub = PublicKey(ByteVector.fromValidHex("028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7"))
    val o_pub = PublicKey(ByteVector.fromValidHex("032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e"))

    val dbx = new TxStorage()
    dbx.save("T", t)
    dbx.save("T1", t1)

    val alice_p = Participant("Alice", List(a_pub), Address("akka", "test", "127.0.0.1", 25000))
    val bob_p = Participant("Bob", List(b_pub), Address("akka", "test", "127.0.0.1", 25001))
    val oracle_p = Participant("Oracle", List(o_pub), Address("akka", "test", "127.0.0.1", 25002))

    //saving the partecipants on a storage
    val partDb = new ParticipantStorage()
    partDb.save(alice_p)
    partDb.save(bob_p)
    partDb.save(oracle_p)
    val t_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2WPKH, chunkPrivacy= ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(a_pub), data = ByteVector.empty))
    val t1_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SIG_P2WSH, chunkPrivacy= ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(b_pub), data = ByteVector.empty),
      ChunkEntry(chunkType = ChunkType.SIG_P2WSH, chunkPrivacy= ChunkPrivacy.PUBLIC, chunkIndex = 1, owner = Option(o_pub), data = ByteVector.empty))
    val t_entry = TxEntry(name = "T", indexData = Map(0 -> IndexEntry(amt = Btc(1).toSatoshi ,chunkData = t_chunks)))
    val t1_entry = TxEntry(name = "T1", indexData = Map(0 -> IndexEntry(amt = Btc(1).toSatoshi ,chunkData = t1_chunks)))

    val metadb = new MetaStorage()
    metadb.save(t_entry)
    metadb.save(t1_entry)

    val initialState = State(partDb, dbx, metadb)
    initialState
  }
}
