; Copyright © 2015-2018 Esko Luontola
; This software is released under the Apache License 2.0.
; The license text is at http://www.apache.org/licenses/LICENSE-2.0

(ns territory-bro.authenticaton-test
  (:require [clojure.test :refer :all]
            [territory-bro.authentication :refer :all])
  (:import (com.auth0.jwk JwkProvider Jwk)
           (java.util Map)))

;; key cached from https://luontola.eu.auth0.com/.well-known/jwks.json
(def jwk {"alg" "RS256",
          "kty" "RSA",
          "use" "sig",
          "x5c" ["MIIC8jCCAdqgAwIBAgIJHoFouif+0twQMA0GCSqGSIb3DQEBBQUAMCAxHjAcBgNVBAMTFWx1b250b2xhLmV1LmF1dGgwLmNvbTAeFw0xNjA5MTYwODM4MjhaFw0zMDA1MjYwODM4MjhaMCAxHjAcBgNVBAMTFWx1b250b2xhLmV1LmF1dGgwLmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAOAvQieNzD29VsOdQc3YHPzpLkNkShpeMuFYB76WHRb6UQpUBAKSEVpxvu1G0DG2shMJ+DObsQ81ID+WFYW445Dz6sJE4dRGmSx9oEGPB7kiDGZx1bb2O14n6v17/qzz2PHgCT05BIU+AmrpN5GNZdnJya0jU4r0UQInDRD5/qZwUF8oXfcG7eewcYLak7ZwsjA1Kf4HADkMIZo8NZ+9TtvN2cToPzPtlGSInsjW7oZP1m/qO4xvEyAQUtj11QV8so9F5NPyd9h5PYlo5t792I4bOUykpck1KR81RUJuZ3HLt5104JNFYcEe2tjnt9DtBAXfMvMtdiJZ85BRE9XJ9NMCAwEAAaMvMC0wDAYDVR0TBAUwAwEB/zAdBgNVHQ4EFgQUj8UVIFeOuo6D0UE3eogA7Ht623cwDQYJKoZIhvcNAQEFBQADggEBAJfJ5yi3Akh9pGD+PMiN0AqzT9kJr6g6Z3EZJ0qP6iiGZLsMBSDeulpjWMOeYvbW0OCKJI8X+YidXAlxOGNyIxkyu8IXJ7E5Z+DSg4H9D6YG26VQKqKsorhZ2YxRsckagaMEYqH7KIKesS5iiK32ULR5iV5+NdBGafoNLNwBxX6Pge1f2QskJJy22vWlh9NA2jmBbCIl5OzNxEouMn34jCnq/F+zg0fDEAOM9ZdcsjXRMT3a2Dta7L4G9bnkX8a9gGe6cRcqINeaIMY4/Jpp6Lb6t1lvWYG+TbhWAoeHl3ZfqjNm4cnnvoNAkiVLC73rC7SHhzzyKDwZS8p31QtEB1E="],
          "n" "4C9CJ43MPb1Ww51Bzdgc_OkuQ2RKGl4y4VgHvpYdFvpRClQEApIRWnG-7UbQMbayEwn4M5uxDzUgP5YVhbjjkPPqwkTh1EaZLH2gQY8HuSIMZnHVtvY7Xifq_Xv-rPPY8eAJPTkEhT4Cauk3kY1l2cnJrSNTivRRAicNEPn-pnBQXyhd9wbt57BxgtqTtnCyMDUp_gcAOQwhmjw1n71O283ZxOg_M-2UZIieyNbuhk_Wb-o7jG8TIBBS2PXVBXyyj0Xk0_J32Hk9iWjm3v3Yjhs5TKSlyTUpHzVFQm5nccu3nXTgk0VhwR7a2Oe30O0EBd8y8y12IlnzkFET1cn00w",
          "e" "AQAB",
          "kid" "RjY1MzA3NTJGRkM1QTkyNUZFMTk3NkU2OTcwQUEwRjEzMjRCQTBCNA",
          "x5t" "RjY1MzA3NTJGRkM1QTkyNUZFMTk3NkU2OTcwQUEwRjEzMjRCQTBCNA"})

(def fake-jwk-provider
  (reify JwkProvider
    (get [this keyId]
      (let [m (.getDeclaredMethod Jwk "fromValues" (into-array Class [Map]))]
        (.setAccessible m true)
        (.invoke m nil (into-array [jwk]))))))

(deftest decode-jwt-test
  (binding [jwk-provider fake-jwk-provider]
    (let [decoded (decode-jwt "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6IlJqWTFNekEzTlRKR1JrTTFRVGt5TlVaRk1UazNOa1UyT1Rjd1FVRXdSakV6TWpSQ1FUQkNOQSJ9.eyJnaXZlbl9uYW1lIjoiRXNrbyIsImZhbWlseV9uYW1lIjoiTHVvbnRvbGEiLCJuaWNrbmFtZSI6ImVza28ubHVvbnRvbGEiLCJuYW1lIjoiRXNrbyBMdW9udG9sYSIsInBpY3R1cmUiOiJodHRwczovL2xoNi5nb29nbGV1c2VyY29udGVudC5jb20vLUFtRHYtVlZoUUJVL0FBQUFBQUFBQUFJL0FBQUFBQUFBQWVJL2JIUDhsVk5ZMWFBL3Bob3RvLmpwZyIsImxvY2FsZSI6ImVuLUdCIiwidXBkYXRlZF9hdCI6IjIwMTgtMTEtMDNUMTE6NTY6NTYuMjg5WiIsImlzcyI6Imh0dHBzOi8vbHVvbnRvbGEuZXUuYXV0aDAuY29tLyIsInN1YiI6Imdvb2dsZS1vYXV0aDJ8MTAyODgzMjM3Nzk0NDUxMTExNDU5IiwiYXVkIjoiOHRWa2Rmbnc4eW5aNnJYTm5kRDZlWjZFcnNIZElnUGkiLCJpYXQiOjE1NDEyNDYyMTYsImV4cCI6MTU0MTI4MjIxNiwibm9uY2UiOiJMYkMxSHZSM3lNUVl6cEtqTEFEODRsM0pSVHg0a3VSdCJ9.L7QG7UBTyORSIiRdpr0fLeSqATJiyQVQOBbbWiWtQEvmkk_rEIy3uBuyxntyc-oHx2hUHhHez1OxH4GvaPrYARI6SYZlGOJXplJEgiYycg0ghmZ-5A4sXrKI694xroRLMDe7OIF754ICmyd-6stuFh4_5TkOBw3uWDNu9K7MW8uz2BWkyZTp37DiYtA_yEEpmnzJmCH91lDfCUWv2i_nNDEoLOQ6KsTe3JJR37_mkz1TKP0EAu02iZajMXEBn_lbJbTDoRLGzrWMrp7pqI3H_qAOqnK5H_lh1PkEIRsDON1yXgOOEKTLrnK9dpZw_RFnLmY1yIJ8SOZTCLD0sWV-1A")]
      (is (= {:name "Esko Luontola"}
             (select-keys decoded [:name]))))))
