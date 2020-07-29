package Managers

import fr.acinq.bitcoin.Btc
import xyz.bitml.api.IndexEntry

object Helpers {
  implicit class StringSeq(x: String) {
    def withRawTx[String](f: => String): Seq[Any] = Seq(x, f)
    def onIndex[String](f: => Int) : Seq[Any] = Seq(x, f)
    def withEntry[IndexEntry](f: => IndexEntry) : Tuple2[String, IndexEntry] = (x, f)
    def withInfo[Partecipant](f: => String, g: => String, h: => Int) : Tuple4[String, String, String, Int] = (x, f, g, h)
  }

  implicit class ChunkSeq(x: Btc) {
    def forChunks[ChunkEntry](f: => Seq[ChunkEntry]) : Tuple2[Btc, Seq[ChunkEntry]] = (x, f)
    def forChunks[ChunkEntry](f: => ChunkEntry) : Tuple2[Btc, Seq[ChunkEntry]] = (x, Seq(f))
  }
}
