package Managers

import akka.actor.Address
import fr.acinq.bitcoin.Crypto.PublicKey
import fr.acinq.bitcoin.{Btc, Transaction}
import scodec.bits.ByteVector
import xyz.bitml.api.ChunkPrivacy.ChunkPrivacy
import xyz.bitml.api.ChunkType.ChunkType
import xyz.bitml.api.persistence.{MetaStorage, ParticipantStorage, State, TxStorage}
import xyz.bitml.api._

case class DbManager() {
  private val txdb = new TxStorage()
  private val partdb = new ParticipantStorage()
  private val metadb = new MetaStorage()

  def addTransaction(name: String, serializedTx: String) : Unit = {
    val tx = Transaction.read(serializedTx)
    txdb.save(name, tx)
  }

  def addTransaction(info : Seq[Any]) : Unit = {
    addTransaction(info(0).toString, info(1).toString)
  }

  def addTransactions(txs: Seq[Any]*) : Unit = {
    for(tx <- txs) {
      addTransaction(tx(0).toString, tx(1).toString)
    }
  }

  /*def addPartecipant(name: String, pubKey: Seq[String], ip: String, port: Int,  protocol: String = "akka", system: String = "test"): Unit = {
    var pubKeys = mutable.MutableList[PublicKey]()
    for (key <- pubKey) {
      pubKeys += PublicKey(ByteVector.fromValidHex(key))
    }
    val partecipant = Participant(name, pubKeys.toList, Address(protocol, system, ip, port))
    partdb.save(partecipant)
  }*/

  def addPartecipant(name: String, pubKey: String, ip: String, port: Int,  protocol: String = "akka", system: String = "test") : Unit = {
    val pub = PublicKey(ByteVector.fromValidHex(pubKey))
    val partecipant = Participant(name, List(pub), Address(protocol, system, ip, port))
    partdb.save(partecipant)
  }

  def addPartecipant(part: Tuple4[String, String, String, Int]) : Unit = {
    addPartecipant(part._1, part._2, part._3, part._4)
  }

  def addPartecipants(info: Tuple4[String, String, String, Int]*) : Unit = {
    for (part <- info) {
      addPartecipant(part)
    }
  }

  def createPublicChunk(pubKey: String, index: Int = 0, chunkType: ChunkType = ChunkType.SIG_P2WPKH, chunkPrivacy: ChunkPrivacy = ChunkPrivacy.PUBLIC, data: ByteVector = ByteVector.empty) : Seq[ChunkEntry] = {
    val publicKey = PublicKey(ByteVector.fromValidHex(pubKey))
    Seq(ChunkEntry(chunkType, chunkPrivacy, index, Option(publicKey), data))
  }

  def createAuthChunk(pubKey: String, index: Int = 0, chunkType: ChunkType = ChunkType.SIG_P2WSH, chunkPrivacy: ChunkPrivacy = ChunkPrivacy.AUTH, data: ByteVector = ByteVector.empty): ChunkEntry = {
    val publicKey = PublicKey(ByteVector.fromValidHex(pubKey))
    ChunkEntry(chunkType, chunkPrivacy, index, Option(publicKey), data)
  }

  def createPublicChunk(info: Seq[Any]) : Seq[ChunkEntry] = {
    createPublicChunk(info(0).toString, info(1).toString.toInt)
  }

  def createAuthChunk(info: Seq[Any]): ChunkEntry = {
    createAuthChunk(info(0).toString, info(1).toString.toInt)
  }

  def createEntry(amount : Btc, chunks : Seq[ChunkEntry]) : IndexEntry = {
    IndexEntry(amount, chunks)
  }

  def createEntry(amount : Btc, chunk : ChunkEntry) : IndexEntry = {
    IndexEntry(amount, Seq(chunk))
  }

  def createEntry(info: (Btc, Seq[ChunkEntry])) : IndexEntry = {
    IndexEntry(info._1, info._2)
  }

  def addMeta(name: String, entries : Seq[IndexEntry]) : Unit = {
    var i = 0
    var data: Map[Int, IndexEntry] = Map()

    for (entry <- entries) {
      data += (i -> entry)
      i += 1
    }
  }

  def addMeta(name: String, entry : IndexEntry) : Unit = {
    val txEntry = TxEntry(name, Map(0 -> entry))
    metadb.save(txEntry)
  }

  def addMeta(info: Tuple2[String, IndexEntry]) : Unit = {
    val txEntry = TxEntry(info._1, Map(0 -> info._2))
    metadb.save(txEntry)
  }

  def addMetas(info: Tuple2[String, IndexEntry]*) : Unit = {
    for (meta <- info) {
      val txEntry = TxEntry(meta._1, Map(0 -> meta._2))
      metadb.save(txEntry)
    }
  }

  def getState: State = {
    State(partdb, txdb, metadb)
  }

  def prepareEntry(chunkEntries: ChunkEntry*): Seq[ChunkEntry] = {
    var isAllZero = true
    var isNoneZero = true
    var zeroCounter = 0
    var i=0

    for (entry <- chunkEntries) {
      if(entry.chunkIndex != 0) {
        isAllZero = false
      } else {
        isNoneZero = false
        zeroCounter+=1
      }
    }

    if(isAllZero) {
      i = 0
      var entries : Seq[ChunkEntry] = Seq()
      for (chunkEntry <- chunkEntries) {
        entries = entries :+ ChunkEntry(chunkEntry.chunkType, chunkEntry.chunkPrivacy, i, chunkEntry.owner, chunkEntry.data)
        i+=1
      }
      entries
    } else if (!isNoneZero && zeroCounter < chunkEntries.length) {
      throw new Exception("Error preparing entry: Misuse of default chunk index values!")
    } else {
      chunkEntries
    }
  }
}
