package org.metal.backend.spark;

import org.apache.spark.sql.SparkSession;
import org.metal.core.BaseMetalService;
import org.metal.core.backend.IBackend;
import org.metal.core.backend.ISetup;
import org.metal.core.props.IMetalProps;

import java.util.*;

public class SparkBackend implements IBackend {
    @Override
    public void start() throws IllegalArgumentException{
        SparkSession.Builder builder = SparkSession.builder();
        for (Map.Entry<String, Object> kv :
                this.confs.entrySet()) {
            if (kv.getValue() instanceof Double) {
                builder.config(kv.getKey(), (double)kv.getValue());
            } else if (kv.getValue() instanceof Long) {
                builder.config(kv.getKey(), (long)kv.getValue());
            } else if (kv.getValue() instanceof String) {
                builder.config(kv.getKey(), (String)kv.getValue());
            } else if (kv.getValue() instanceof Boolean) {
                builder.config(kv.getKey(), (boolean)kv.getValue());
            }
        }
        if (confs.containsKey("master")) {
            String master = (String) confs.get("master");
            builder.master(master);
        } else {
            throw new IllegalArgumentException("Master is not set!");
        }
        if (confs.containsKey("appName")) {
            String appName = (String) confs.get("appName");
            builder.appName(appName);
        } else {
            builder.appName("SparkBackend-"+this.hashCode());
        }

        this.platform = builder.getOrCreate();
        for (ISetup<SparkSession> setup: setups){
            setup.setup(platform);
        }

        SparkTranslator translator = new SparkTranslator(platform);
        this.service = SparkMetalService.of(translator);
    }

    @Override
    public void stop() {
        this.service = null;
        this.platform.stop();
    }

    @Override
    public SparkMetalService service() throws IllegalArgumentException{
        if (this.platform != null && this.service != null) {
            return this.service;
        } else {
            throw new IllegalArgumentException("The SparkBackend is not inited or destoryed.");
        }
    }

    private Set<ISetup<SparkSession>> setups;
    private Map<String, Object> confs;

    private SparkSession platform;
    private SparkMetalService<IMetalProps> service;

    private SparkBackend() {
        this.setups = new HashSet<>();
        this.confs = new HashMap<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private SparkBackend innerBackend;
        private Builder() {
            this.innerBackend = new SparkBackend();
        }

        public Builder conf(String key, Object value) {
            innerBackend.confs.put(key, value);
            return this;
        }

        public Builder setup(ISetup<SparkSession> setup) {
            innerBackend.setups.add(setup);
            return this;
        }

        public SparkBackend build() {
            innerBackend.setups = Collections.unmodifiableSet(innerBackend.setups);
            innerBackend.confs = Collections.unmodifiableMap(innerBackend.confs);
            return innerBackend;
        }
    }
}
