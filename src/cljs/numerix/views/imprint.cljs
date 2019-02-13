(ns numerix.views.imprint
  (:require [numerix.state :as s]
            [taoensso.encore :as enc]
            [secretary.core :refer [dispatch!]]
            [numerix.views.base :as base]
            [re-frame.core :as rf]))

(defn imprint-page []
  (rf/dispatch-sync [:init-form-state {}])
  (fn []
    (base/base
               [:div {:key (enc/uuid-str 5)}
                [:div.row.text-left
                 [:div.col-md-6
                  [:h2 "Imprint / Impressum"]]
                 [:div.col-md-6
                  [:h4 [:a {:href "/terms" :target "_blank"} "Click here to review our terms of service."]]]]
                [:div.row.text-left
                 [:div.col-md-6
                  [:h3 "Angaben gemäß § 5 TMG:"]
                  [:p
                   "AGYNAMIX Torsten Uhlmann" [:br]
                   "Buchenweg 5" [:br]
                   "09380 Thalheim"]]
                 [:div.col-md-6
                  [:h3 "Kontakt"]
                  [:address
                   [:strong "Address"] [:br]
                   "AGYNAMIX Torsten Uhlmann" [:br]
                   "Buchenweg 5" [:br]
                   "09380 Thalheim" [:br] [:br]
                   [:strong "Erreichbar unter:"] [:br]
                   "Telefon: 03721 273445" [:br]
                   "Fax: 03721 273446" [:br]
                   "Email: contact@agynamix.de"]]]
                [:div.row.text-left
                 [:div.col-md-6
                  [:h3 "Umsatzsteuer-ID"]
                  [:p
                   "Umsatzsteuer-Identifikationsnummer gemäß §27 a Umsatzsteuergesetz:" [:br]
                   "DE814106145"]]
                 [:div.col-md-6
                  [:h3 "Verantwortlich für den Inhalt nach §55 Abs. 2 RStV"]
                  [:p
                   "AGYNAMIX Torsten Uhlmann<br>Buchenweg 5" [:br] "09380 Thalheim"]]]
                [:div.row.text-left
                 [:div.col-md-12
                  [:h3 "Haftungsausschluss"]
                  [:p
                   [:strong "Haftung für Inhalte"]]
                  [:p
                   "Die Inhalte unserer Seiten wurden mit größter Sorgfalt erstellt."
                   "Für die Richtigkeit, Vollständigkeit und Aktualität der Inhalte können wir jedoch keine Gewähr übernehmen."
                   "Als Diensteanbieter sind wir gemäß § 7 Abs.1 TMG für eigene Inhalte auf diesen Seiten nach den allgemeinen"
                   "Gesetzen verantwortlich. Nach §§ 8 bis 10 TMG sind wir als Diensteanbieter jedoch nicht verpflichtet,"
                   "übermittelte oder gespeicherte fremde Informationen zu überwachen oder nach Umständen zu forschen,"
                   "die auf eine rechtswidrige Tätigkeit hinweisen. Verpflichtungen zur Entfernung oder Sperrung der Nutzung"
                   "von Informationen nach den allgemeinen Gesetzen bleiben hiervon unberührt. Eine diesbezügliche Haftung"
                   "ist jedoch erst ab dem Zeitpunkt der Kenntnis einer konkreten Rechtsverletzung möglich."
                   "Bei Bekanntwerden von entsprechenden Rechtsverletzungen werden wir diese Inhalte umgehend entfernen."]
                  [:p
                   [:strong "Haftung für Links"]]
                  [:p
                   "Unser Angebot enthält Links zu externen Webseiten Dritter, auf deren Inhalte wir keinen Einfluss haben."
                   "Deshalb können wir für diese fremden Inhalte auch keine Gewähr übernehmen."
                   "Für die Inhalte der verlinkten Seiten ist stets der jeweilige Anbieter oder Betreiber der Seiten verantwortlich."
                   "Die verlinkten Seiten wurden zum Zeitpunkt der Verlinkung auf mögliche Rechtsverstöße überprüft."
                   "Rechtswidrige Inhalte waren zum Zeitpunkt der Verlinkung nicht erkennbar. Eine permanente inhaltliche"
                   "Kontrolle der verlinkten Seiten ist jedoch ohne konkrete Anhaltspunkte einer Rechtsverletzung nicht"
                   "zumutbar. Bei Bekanntwerden von Rechtsverletzungen werden wir derartige Links umgehend entfernen."]
                  [:p
                   [:strong "Urheberrecht"]]
                  [:p
                   "Die durch die Seitenbetreiber erstellten Inhalte und Werke auf diesen Seiten unterliegen dem deutschen Urheberrecht."
                   "Die Vervielfältigung, Bearbeitung, Verbreitung und jede Art der Verwertung außerhalb der Grenzen des"
                   "Urheberrechtes bedürfen der schriftlichen Zustimmung des jeweiligen Autors bzw. Erstellers."
                   "Downloads und Kopien dieser Seite sind nur für den privaten, nicht kommerziellen Gebrauch gestattet."
                   "Soweit die Inhalte auf dieser Seite nicht vom Betreiber erstellt wurden, werden die Urheberrechte"
                   "Dritter beachtet. Insbesondere werden Inhalte Dritter als solche gekennzeichnet. Sollten Sie trotzdem auf"
                   "eine Urheberrechtsverletzung aufmerksam werden, bitten wir um einen entsprechenden Hinweis."
                   "Bei Bekanntwerden von Rechtsverletzungen werden wir derartige Inhalte umgehend entfernen."]
                  [:p
                   [:strong "Datenschutz"]]
                  [:p
                   "Die Nutzung unserer Webseite ist in der Regel ohne Angabe personenbezogener Daten möglich."
                   "Soweit auf unseren Seiten personenbezogene Daten (beispielsweise Name, Anschrift oder eMail-Adressen)"
                   "erhoben werden, erfolgt dies, soweit möglich, stets auf freiwilliger Basis. Diese Daten werden ohne Ihre"
                   "ausdrückliche Zustimmung nicht an Dritte weitergegeben."]
                  [:p
                   "Wir weisen darauf hin, dass die Datenübertragung im Internet (z.B. bei der Kommunikation per E-Mail)"
                   "Sicherheitslücken aufweisen kann. Ein lückenloser Schutz der Daten vor dem Zugriff durch Dritte ist nicht möglich."]
                  [:p
                   "Der Nutzung von im Rahmen der Impressumspflicht veröffentlichten Kontaktdaten durch Dritte zur"
                   "Übersendung von nicht ausdrücklich angeforderter Werbung und Informationsmaterialien wird hiermit"
                   "ausdrücklich widersprochen. Die Betreiber der Seiten behalten sich ausdrücklich rechtliche Schritte"
                   "im Falle der unverlangten Zusendung von Werbeinformationen, etwa durch Spam-Mails, vor."]
                  [:p ""]
                  [:p
                   [:strong "Datenschutzerklärung für die Nutzung von Google	Analytics"]]
                  [:p
                   "Diese Website benutzt Google Analytics, einen Webanalysedienst der Google Inc. ('Google')."
                   "Google Analytics verwendet sog. 'Cookies', Textdateien, die auf Ihrem Computer gespeichert werden"
                   "und die eine Analyse der Benutzung der Website durch Sie ermöglichen. Die durch den Cookie erzeugten"
                   "Informationen über Ihre Benutzung dieser Website werden in der Regel an einen Server von Google"
                   "in den USA übertragen und dort gespeichert. Im Falle der Aktivierung der IP-Anonymisierung auf dieser"
                   "Webseite wird Ihre IP-Adresse von Google jedoch innerhalb von Mitgliedstaaten der Europäischen Union oder"
                   "in anderen Vertragsstaaten des Abkommens über den Europäischen Wirtschaftsraum zuvor gekürzt."]
                  [:p
                   "Nur in Ausnahmefällen wird die volle IP-Adresse an einen Server von Google in den USA übertragen und dort gekürzt."
                   "Im Auftrag des Betreibers dieser Website wird Google diese Informationen benutzen, um Ihre Nutzung der Website"
                   "auszuwerten, um Reports über die Websiteaktivitäten zusammenzustellen und um weitere mit der Websitenutzung"
                   "und der Internetnutzung verbundene Dienstleistungen gegenüber dem Websitebetreiber zu erbringen."
                   "Die im Rahmen von Google Analytics von Ihrem Browser übermittelte IP-Adresse wird nicht mit anderen Daten von"
                   "Google zusammengeführt."]
                  [:p
                   "Sie können die Speicherung der Cookies durch eine entsprechende Einstellung Ihrer Browser-Software"
                   "verhindern; wir weisen Sie jedoch darauf hin, dass Sie in diesem Fall gegebenenfalls nicht"
                   "sämtliche Funktionen dieser Website vollumfänglich werden nutzen können. Sie können darüber hinaus die"
                   "Erfassung der durch das Cookie erzeugten und auf Ihre Nutzung der Website bezogenen Daten"
                   "(inkl. Ihrer IP-Adresse) an Google sowie die Verarbeitung dieser Daten durch Google verhindern,"
                   "indem sie das unter dem folgenden Link verfügbare Browser-Plugin herunterladen und installieren:"
                   [:a {:href "http://tools.google.com/dlpage/gaoptout?hl=de"
                        :style {:word-break "break-all"}
                        } "http://tools.google.com/dlpage/gaoptout?hl=de."]]
                  [:p ""]
                  [:p
                   [:strong "Datenschutzerklärung für die Nutzung von Google	Adsense"]]
                  [:p
                   "Diese Website benutzt Google AdSense, einen Dienst zum Einbinden von Werbeanzeigen der Google Inc. ('Google')."
                   "Google AdSense verwendet sog. 'Cookies', Textdateien, die auf Ihrem Computer gespeichert werden und"
                   "die eine Analyse der Benutzung der Website ermöglicht. Google AdSense verwendet auch so genannte"
                   "Web Beacons (unsichtbare Grafiken). Durch diese Web Beacons können Informationen wie der Besucherverkehr"
                   "auf diesen Seiten ausgewertet werden."]
                  [:p
                   "Die durch Cookies und Web Beacons erzeugten Informationen über die Benutzung dieser Website"
                   "(einschließlich Ihrer IP-Adresse) und Auslieferung von Werbeformaten werden an einen Server von Google"
                   "in den USA übertragen und dort gespeichert. Diese Informationen können von Google an Vertragspartner von"
                   "Google weiter gegeben werden. Google wird Ihre IP-Adresse jedoch nicht mit anderen von Ihnen gespeicherten"
                   "Daten zusammenführen."]
                  [:p
                   "Sie können die Installation der Cookies durch eine entsprechende Einstellung Ihrer Browser Software"
                   "verhindern; wir weisen Sie jedoch darauf hin, dass Sie in diesem Fall gegebenenfalls nicht sämtliche"
                   "Funktionen dieser Website voll umfänglich nutzen können. Durch die Nutzung dieser Website erklären Sie"
                   "sich mit der Bearbeitung der über Sie erhobenen Daten durch Google in der zuvor beschriebenen Art und Weise"
                   "und zu dem zuvor benannten Zweck einverstanden."]
                  [:p ""]
                  [:p
                   [:i "Quellenangaben: " [:a {:target "_blank"
                                               :href   "http://www.e-recht24.de/muster-disclaimer.htm"}
                                           "Disclaimer eRecht24"] ", "
                    [:a {:target "_blank"
                         :href   "http://www.google.com/intl/de_ALL/analytics/tos.html"}
                     "Google Analytics Datenschutzerklärung"] ", "
                    [:a {:target "_blank"
                         :href   "http://www.e-recht24.de/artikel/datenschutz/6635-datenschutz-rechtliche-risiken-bei-der-nutzung-von-google-analytics-und-googleadsense.html"}
                     "Google Adsense Datenschutzerklärung"]]]]]]
               )))