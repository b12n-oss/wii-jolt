;; Loaded by `jolt build --target tpb32b` in place of the plain cross xpatch
;; (scripts/make-pack installs it as <pack>/xpatch, the real one as
;; <pack>/xpatch.chez).
;;
;; Why: jolt's host runtime applies CHECKED fixnum ops to Java-int-width values
;; (e.g. `(fx>=? k -2147483648)`, `(fx- i #x100000000)`). On a 64-bit Chez
;; those are fixnums; on a 32-bit target (fixnum range ±2^29) they're bignums
;; and the op raises, e.g. "Exception in fx>=?: -2147483648 is not a fixnum"
;; during runtime init. jolt's CHANGELOG (#801) says as much: the hash engine
;; was fixed for 32-bit, "other host code still applies checked fx ops to
;; int-width values, which raise rather than corrupt".
;;
;; For this cross compile only, the checked fx operators below expand to the
;; generic ones. On fixnum arguments generic and fx agree, so behaviour is
;; unchanged where the code was already correct. Explicit #3%fx references
;; (the hash engine's wide arm) are untouched; the narrow arm is selected with
;; JOLT_NARROW_HASH=1, which jolt's define-width-op honours.
;;
;; Spike-grade. The real fix is upstream: consult the TARGET's fixnum width,
;; and use generic ops on int-width values. See docs/jolt-on-wii.md.

(load "@XPATCH_CHEZ@")

(define-syntax define-generic-alias
  (syntax-rules ()
    ((_ (fx generic) ...)
     (begin
       (define-syntax fx
         (identifier-syntax generic))
       ...))))

(define-generic-alias
  (fx=  =) (fx<  <) (fx>  >) (fx<= <=) (fx>= >=)
  (fx=? =) (fx<? <) (fx>? >) (fx<=? <=) (fx>=? >=)
  (fx+ +) (fx- -) (fx* *)
  (fx1+ 1+) (fx1- 1-)
  (fxquotient quotient) (fxremainder remainder) (fxmodulo modulo)
  (fxabs abs) (fxmin min) (fxmax max)
  (fxzero? zero?) (fxpositive? positive?) (fxnegative? negative?)
  (fxnonnegative? nonnegative?) (fxnonpositive? nonpositive?)
  (fxeven? even?) (fxodd? odd?)
  (fxand bitwise-and) (fxior bitwise-ior) (fxxor bitwise-xor) (fxnot bitwise-not)
  (fxlogand logand) (fxlogor logor) (fxlogxor logxor) (fxlognot lognot)
  (fxsll bitwise-arithmetic-shift-left) (fxsra bitwise-arithmetic-shift-right)
  (fxarithmetic-shift-left bitwise-arithmetic-shift-left)
  (fxarithmetic-shift-right bitwise-arithmetic-shift-right))
