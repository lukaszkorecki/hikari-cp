(ns hikari-cp.core-test
  (:require
   [hikari-cp.core :as hikari-cp]
   [expectations :refer [expect]])
  (:import
   (com.codahale.metrics MetricRegistry)
   (com.codahale.metrics.health HealthCheckRegistry)
   com.zaxxer.hikari.HikariConfig
   (com.zaxxer.hikari.metrics.prometheus PrometheusMetricsTrackerFactory)
   (com.zaxxer.hikari.pool HikariPool$PoolInitializationException)))

(def valid-options
  {:auto-commit              false
   :read-only                true
   :connection-timeout       1000
   :validation-timeout       1000
   :idle-timeout             0
   :max-lifetime             0
   :minimum-idle             0
   :maximum-pool-size        1
   :pool-name                "db-pool"
   :adapter                  "postgresql"
   :username                 "username"
   :password                 "password"
   :database-name            "database"
   :server-name              "host-1"
   :port-number              5433
   :connection-init-sql      "set join_collapse_limit=4"
   :connection-test-query    "select 0"
   :register-mbeans          true
   :transaction-isolation    "TRANSACTION_SERIALIZABLE"
   #_:leak-detection-threshold #_4000})                     ; a valid option but tested separately below.

(def alternate-valid-options
  {:driver-class-name "org.postgresql.ds.PGPoolingDataSource"
   :jdbc-url          "jdbc:postgresql://localhost:5433/test"})

(def alternate-valid-options2
  {:datasource-class-name "com.sybase.jdbc3.jdbc.SybDataSource"})

(def metric-registry-options
  {:metric-registry (MetricRegistry.)})

(def health-check-registry-options
  {:health-check-registry (HealthCheckRegistry.)})

(def metrics-tracker-factory-options
  {:metrics-tracker-factory (PrometheusMetricsTrackerFactory.)})

(def ^HikariConfig datasource-config-with-required-settings
  (hikari-cp/datasource-config (apply dissoc valid-options (keys hikari-cp/default-datasource-options))))

(def ^HikariConfig datasource-config-with-overrides
  (hikari-cp/datasource-config valid-options))

(def ^HikariConfig datasource-config-with-overrides-alternate
  (hikari-cp/datasource-config (-> (dissoc valid-options :adapter)
                         (merge alternate-valid-options))))

(def ^HikariConfig datasource-config-with-overrides-alternate2
  (hikari-cp/datasource-config (-> (dissoc valid-options :adapter)
                         (merge alternate-valid-options2))))

(def ^HikariConfig mysql8-datasource-config
  (hikari-cp/datasource-config (merge valid-options {:adapter "mysql8"})))

(def ^HikariConfig mysql-datasource-config
  (hikari-cp/datasource-config (merge valid-options
                            {:adapter "mysql"
                             :use-legacy-datetime-code false})))

(def  ^HikariConfig metric-registry-config
  (hikari-cp/datasource-config (merge valid-options metric-registry-options)))

(def ^HikariConfig health-check-registry-config
  (hikari-cp/datasource-config (merge valid-options health-check-registry-options)))

(def ^HikariConfig metrics-tracker-factory-config
  (hikari-cp/datasource-config (merge valid-options metrics-tracker-factory-options)))

(expect false
        (get (.getDataSourceProperties ^HikariConfig mysql-datasource-config) "useLegacyDatetimeCode"))
(expect "com.mysql.jdbc.jdbc2.optional.MysqlDataSource"
        (.getDataSourceClassName mysql-datasource-config))
(expect "com.mysql.cj.jdbc.MysqlDataSource"
  (.getDataSourceClassName mysql8-datasource-config))
(expect true
        (.isAutoCommit datasource-config-with-required-settings))
(expect false
        (.isReadOnly datasource-config-with-required-settings))
(expect 30000
        (.getConnectionTimeout datasource-config-with-required-settings))
(expect 5000
        (.getValidationTimeout datasource-config-with-required-settings))
(expect 600000
        (.getIdleTimeout datasource-config-with-required-settings))
(expect 1800000
        (.getMaxLifetime datasource-config-with-required-settings))
(expect 10
        (.getMinimumIdle datasource-config-with-required-settings))
(expect 10
        (.getMaximumPoolSize datasource-config-with-required-settings))
(expect "org.postgresql.ds.PGSimpleDataSource"
        (.getDataSourceClassName datasource-config-with-required-settings))
(expect "username"
        (.getUsername datasource-config-with-required-settings))
(expect "password"
        (.getPassword datasource-config-with-required-settings))
(expect 5433
        (-> datasource-config-with-required-settings
            .getDataSourceProperties
            (get "portNumber")))
(expect nil
        (.getMetricRegistry datasource-config-with-required-settings))
(expect (:metric-registry metric-registry-options)
        (.getMetricRegistry metric-registry-config))

(expect nil
  (.getHealthCheckRegistry datasource-config-with-required-settings))
(expect (:health-check-registry health-check-registry-options)
  (.getHealthCheckRegistry health-check-registry-config))

(expect nil
  (.getMetricsTrackerFactory datasource-config-with-required-settings))
(expect (:metrics-tracker-factory metrics-tracker-factory-options)
  (.getMetricsTrackerFactory metrics-tracker-factory-config))

(expect "TRANSACTION_SERIALIZABLE"
  (.getTransactionIsolation datasource-config-with-required-settings))

(expect false
        (.isAutoCommit datasource-config-with-overrides))
(expect true
        (.isReadOnly datasource-config-with-overrides))
(expect 1000
        (.getConnectionTimeout datasource-config-with-overrides))
(expect 1000
        (.getValidationTimeout datasource-config-with-overrides))
(expect 0
        (.getIdleTimeout datasource-config-with-overrides))
(expect 0
        (.getMaxLifetime datasource-config-with-overrides))
(expect 0
        (.getMinimumIdle datasource-config-with-overrides))
(expect 1
        (.getMaximumPoolSize datasource-config-with-overrides))
(expect "db-pool"
        (.getPoolName datasource-config-with-overrides))
(expect "set join_collapse_limit=4"
        (.getConnectionInitSql datasource-config-with-overrides))
(expect "select 0"
        (.getConnectionTestQuery datasource-config-with-overrides))
(expect true
        (.isRegisterMbeans datasource-config-with-overrides))

(expect "org.postgresql.ds.PGPoolingDataSource"
          (.getDriverClassName datasource-config-with-overrides-alternate))
(expect "jdbc:postgresql://localhost:5433/test"
        (.getJdbcUrl datasource-config-with-overrides-alternate))

(expect "com.sybase.jdbc3.jdbc.SybDataSource"
        (.getDataSourceClassName datasource-config-with-overrides-alternate2))

(expect IllegalArgumentException
        (hikari-cp/datasource-config (dissoc valid-options :adapter)))
(expect #"contains\? % :adapter"
        (try
          (hikari-cp/datasource-config (hikari-cp/validate-options (dissoc valid-options :adapter)))
          (catch IllegalArgumentException e
            (str (.getMessage e)))))

(expect "jdbc:postgres:test"
        (.getJdbcUrl (hikari-cp/datasource-config {:jdbc-url "jdbc:postgres:test"})))

(expect map?
        (hikari-cp/validate-options valid-options))
(expect IllegalArgumentException
        (hikari-cp/validate-options (merge valid-options {:auto-commit 1})))
(expect IllegalArgumentException
        (hikari-cp/validate-options (merge valid-options {:read-only 1})))
(expect IllegalArgumentException
        (hikari-cp/validate-options (merge valid-options {:connection-timeout "foo"})))
(expect IllegalArgumentException
        (hikari-cp/validate-options (merge valid-options {:connection-timeout 999})))
(expect IllegalArgumentException
        (hikari-cp/validate-options (merge valid-options {:validation-timeout 999})))
(expect IllegalArgumentException
        (hikari-cp/validate-options (merge valid-options {:idle-timeout -1})))
(expect IllegalArgumentException
        (hikari-cp/validate-options (merge valid-options {:max-lifetime -1})))
(expect IllegalArgumentException
        (hikari-cp/validate-options (merge valid-options {:minimum-idle -1})))
(expect IllegalArgumentException
        (hikari-cp/validate-options (merge valid-options {:maximum-pool-size -1})))
(expect IllegalArgumentException
        (hikari-cp/validate-options (merge valid-options {:maximum-pool-size 0})))
(expect IllegalArgumentException
        (hikari-cp/validate-options (merge valid-options {:adapter :foo})))
(expect IllegalArgumentException
        (hikari-cp/validate-options (merge valid-options {:datasource-classname "adsf"})))
(expect IllegalArgumentException
        (hikari-cp/validate-options (merge (dissoc valid-options :adapter) {:jdbc-url nil})))
(expect IllegalArgumentException
        (hikari-cp/validate-options (merge (dissoc valid-options :adapter) {:jdbc-url "jdbc:h2:~/test"
                                                                  :driver-class-name nil})))
(expect IllegalArgumentException
        (hikari-cp/validate-options (merge valid-options {:transaction-isolation 1})))

(expect map?
        (hikari-cp/validate-options (merge valid-options {:username nil})))
(expect map?
        (hikari-cp/validate-options (dissoc valid-options :username)))
(expect map?
        (hikari-cp/validate-options (dissoc valid-options :password)))
(expect map?
        (hikari-cp/validate-options (merge valid-options {:password nil})))
(expect map?
        (hikari-cp/validate-options (merge valid-options {:database-name nil})))
(expect map?
        (hikari-cp/validate-options (dissoc valid-options :database-name)))
(expect map?
        (hikari-cp/validate-options (dissoc valid-options :server-name)))
(expect map?
        (hikari-cp/validate-options (merge valid-options {:server-name nil})))
(expect map?
        (hikari-cp/validate-options (merge valid-options {:port-number -1})))
(expect map?
        (hikari-cp/validate-options (dissoc valid-options :port-number)))
(expect map?
        (hikari-cp/validate-options (merge (dissoc valid-options :adapter) {:jdbc-url "jdbc:h2:~/test"})))
(expect map?
        (hikari-cp/validate-options (merge (dissoc valid-options :adapter) {:jdbc-url "jdbc:h2:~/test"
                                                                  :driver-class-name "org.h2.Driver"})))


;; -- check leak detections option
;; default should stay 0
(expect 0 (-> valid-options
              (hikari-cp/datasource-config)
              (.getLeakDetectionThreshold)))

;; it should apply a correct value
(let [config (hikari-cp/datasource-config (assoc valid-options :leak-detection-threshold 3000))]
  (expect 3000 (.getLeakDetectionThreshold config)))

;; it should complain, that value is too small
(expect IllegalArgumentException
  (hikari-cp/validate-options (assoc valid-options :leak-detection-threshold 1)))
(expect IllegalArgumentException
  (hikari-cp/validate-options (assoc valid-options :leak-detection-threshold 1999)))

;; Ensure that core options aren't being set as datasource properties
(expect #{"portNumber" "databaseName" "serverName"}
  (set (keys (.getDataSourceProperties metric-registry-config))))

(expect HikariPool$PoolInitializationException
  (hikari-cp/make-datasource valid-options))

(expect "tinyInt1isBit" (hikari-cp/translate-property :tinyInt1isBit))
(expect "tinyInt1isBit" (hikari-cp/translate-property :tiny-int1is-bit))
(expect "useSSL" (hikari-cp/translate-property :useSSL))
(expect "useSSL" (hikari-cp/translate-property :use-ssl))
(expect "useFoo" (hikari-cp/translate-property :useFOO))

;; translate-property is extensible
(defmethod hikari-cp/translate-property ::extend-translate-test [_] 42)
(expect 42 (hikari-cp/translate-property ::extend-translate-test))
