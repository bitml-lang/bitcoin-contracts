package Tests_Decentralized

import akka.actor.Address
import fr.acinq.bitcoin.Crypto.PublicKey
import xyz.bitml.api.persistence._
import fr.acinq.bitcoin._
import scodec.bits.ByteVector
import xyz.bitml.api.ChunkPrivacy.ChunkPrivacy
import xyz.bitml.api.ChunkType.ChunkType
import xyz.bitml.api.{ChunkEntry, ChunkPrivacy, ChunkType, IndexEntry, Participant, TxEntry}

import scala.collection.mutable

case class DbManager() {
  private val txdb = new TxStorage()
  private val partdb = new ParticipantStorage()
  private val metadb = new MetaStorage()

  def addTransaction(name: String, serializedTx: String) : Unit = {
    val tx = Transaction.read(serializedTx)
    txdb.save(name, tx)
  }

  def addPartecipant(name: String, pubKey: Seq[String], ip: String, port: Int,  protocol: String = "akka", system: String = "test"): Unit = {
    var pubKeys = mutable.MutableList[PublicKey]()
    for (key <- pubKey) {
      pubKeys += PublicKey(ByteVector.fromValidHex(key))
    }
    val partecipant = Participant(name, pubKeys.toList, Address(protocol, system, ip, port))
    partdb.save(partecipant)
  }

  def createChunk(pubKey: String, index: Int, chunkType: ChunkType = ChunkType.SIG_P2WPKH, chunkPrivacy: ChunkPrivacy = ChunkPrivacy.PUBLIC, data: ByteVector = ByteVector.empty ) : ChunkEntry = {
    val publicKey = PublicKey(ByteVector.fromValidHex(pubKey))
    ChunkEntry(chunkType, chunkPrivacy, index, Option(publicKey), data)
  }

  def createEntry(amount : Btc, chunks : Seq[ChunkEntry]) : IndexEntry = {
    IndexEntry(amount, chunks)
  }

  def addMeta(name: String, entries : Seq[IndexEntry]) : Unit = {
    var i = 0
    var data:Map[Int, IndexEntry] = Map()

    for (entry <- entries) {
      data += (i -> entry)
      i+=1
    }

    val txEntry = TxEntry(name, data)
    metadb.save(txEntry)
  }

  def getState: State = {
    State(partdb, txdb, metadb)
  }
}
