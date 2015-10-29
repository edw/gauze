(defproject org.clojars.edw/gauze "0.2.0"
  :description "A simple library for doing SQL"
  :url "http://github.com/edw/gauze"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [postgresql "9.1-901.jdbc4"]
                 [c3p0/c3p0 "0.9.1.2"]
                 [log4j "1.2.15" :exclusions [javax.mail/mail
                                              javax.jms/jms
                                              com.sun.jdmk/jmxtools
                                              com.sun.jmx/jmxri]]])
