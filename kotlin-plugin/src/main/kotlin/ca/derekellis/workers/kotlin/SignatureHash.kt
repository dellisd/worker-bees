package ca.derekellis.workers.kotlin

import java.security.MessageDigest
import java.util.Base64

/** This would be more compact with Okio, but adding that dependency is tricky. */
fun String.signatureHash(): String {
  val signatureUtf8 = encodeToByteArray()
  val sha256 = MessageDigest.getInstance("SHA-256").digest(signatureUtf8)
  val sha256Prefix = sha256.sliceArray(0 until 6)
  return String(Base64.getEncoder().encode(sha256Prefix))
}
