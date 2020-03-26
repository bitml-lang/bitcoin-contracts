import fr.acinq.bitcoin
import fr.acinq.bitcoin._
import fr.acinq.bitcoin.Crypto._
import scodec.bits._

object bitcoinTest {
  def main(args: Array[String]): Unit = {
    baseTxs()
  }

  def baseTxs() : Unit = {
    val privateKeyAlice = PrivateKey.fromBase58("cRp4uUnreGMZN8vB7nQFX6XWMHU5Lc73HMAhmcDEwHfbgRS66Cqp", Base58.Prefix.SecretKeyTestnet)._1
    val publicKeyAlice = privateKeyAlice.publicKey
    val amount = 10000 sat

    val privateKeyBob = PrivateKey.fromBase58("cVifQzXqqQ86udHggaDMz4Uq66Z7RGXJo5PdVjzRP12H1NDCFsLV", Base58.Prefix.SecretKeyTestnet)._1
    val publicKeyBob = privateKeyBob.publicKey

    val startTx = Transaction.read("0100000001b021a77dcaad3a2da6f1611d2403e1298a902af8567c25d6e65073f6b52ef12d000000006a473044022056156e9f0ad7506621bc1eb963f5133d06d7259e27b13fcb2803f39c7787a81c022056325330585e4be39bcf63af8090a2deff265bc29a3fb9b4bf7a31426d9798150121022dfb538041f111bb16402aa83bd6a3771fa8aa0e5e9b0b549674857fafaf4fe0ffffffff0210270000000000001976a91415c23e7f4f919e9ff554ec585cb2a67df952397488ac3c9d1000000000001976a9148982824e057ccc8d4591982df71aa9220236a63888ac00000000")

    // create a transaction where the sig script is the pubkey script of the tx we want to redeem
    // the pubkey script is just a wrapper around the pub key hash
    // what it means is that we will sign a block of data that contains txid + from + to + amount

    // step  #1: creation a new transaction that reuses the previous transaction's output pubkey script
    val tx1 = Transaction(
      version = 1L,
      txIn = List(
        TxIn(OutPoint(startTx, 0), signatureScript = Nil, sequence = 0xFFFFFFFFL)
      ),
      txOut = List(
        TxOut(amount = amount, publicKeyScript = OP_DUP :: OP_HASH160 :: OP_PUSHDATA(privateKeyBob.publicKey) :: OP_EQUALVERIFY :: OP_CHECKSIG :: Nil)
      ),
      lockTime = 0L
    )

    // step #2: sign the tx
    val sig1 = Transaction.signInput(tx1, 0, startTx.txOut(0).publicKeyScript, SIGHASH_ALL, 0 sat, SigVersion.SIGVERSION_BASE, privateKeyAlice)
    val tx2 = tx1.updateSigScript(0, OP_PUSHDATA(sig1) :: OP_PUSHDATA(privateKeyAlice.publicKey) :: Nil)

    Transaction.correctlySpends(tx2, Seq(startTx), ScriptFlags.MANDATORY_SCRIPT_VERIFY_FLAGS)

    //other tx
    val tx3 = Transaction(
      version = 1L,
      txIn = List(
        TxIn(OutPoint(tx2, 0), signatureScript = Nil, sequence = 0xFFFFFFFFL)
      ),
      txOut = List(
        TxOut(amount = amount, publicKeyScript = OP_DUP :: OP_HASH160 :: OP_PUSHDATA(privateKeyAlice.publicKey) :: OP_EQUALVERIFY :: OP_CHECKSIG :: Nil)
      ),
      lockTime = 0L
    )

    val sig2 = Transaction.signInput(tx3, 0, tx2.txOut(0).publicKeyScript, SIGHASH_ALL, 50 sat, SigVersion.SIGVERSION_BASE, privateKeyBob)
    val tx4 = tx3.updateSigScript(0, OP_PUSHDATA(sig2) :: OP_PUSHDATA(privateKeyBob.publicKey) :: Nil)
    Transaction.correctlySpends(tx4, Seq(tx2), ScriptFlags.MANDATORY_SCRIPT_VERIFY_FLAGS)

  }
}
