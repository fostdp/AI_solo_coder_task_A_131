CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS nest_cart (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    boom_length DOUBLE PRECISION NOT NULL DEFAULT 8.0,
    boom_cross_section_area DOUBLE PRECISION NOT NULL DEFAULT 0.01,
    boom_moment_of_inertia DOUBLE PRECISION NOT NULL DEFAULT 8.33e-6,
    boom_elastic_modulus DOUBLE PRECISION NOT NULL DEFAULT 1.2e10,
    basket_weight DOUBLE PRECISION NOT NULL DEFAULT 150.0,
    base_height DOUBLE PRECISION NOT NULL DEFAULT 4.0,
    max_height DOUBLE PRECISION NOT NULL DEFAULT 15.0,
    stress_limit DOUBLE PRECISION NOT NULL DEFAULT 8.0e6,
    sway_limit DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sensor_data (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cart_id UUID NOT NULL REFERENCES nest_cart(id),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    boom_stress DOUBLE PRECISION NOT NULL,
    basket_sway DOUBLE PRECISION NOT NULL,
    height DOUBLE PRECISION NOT NULL,
    observation_distance DOUBLE PRECISION NOT NULL,
    wind_speed DOUBLE PRECISION DEFAULT 0.0,
    wind_direction DOUBLE PRECISION DEFAULT 0.0,
    temperature DOUBLE PRECISION DEFAULT 20.0
);

CREATE TABLE IF NOT EXISTS alert_record (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cart_id UUID NOT NULL REFERENCES nest_cart(id),
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'WARNING',
    message TEXT NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    threshold DOUBLE PRECISION NOT NULL,
    acknowledged BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS terrain_elevation (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    grid_x INTEGER NOT NULL,
    grid_y INTEGER NOT NULL,
    elevation DOUBLE PRECISION NOT NULL,
    resolution DOUBLE PRECISION NOT NULL DEFAULT 10.0,
    region_name VARCHAR(100),
    UNIQUE(grid_x, grid_y, region_name)
);

CREATE TABLE IF NOT EXISTS vision_analysis_result (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cart_id UUID NOT NULL REFERENCES nest_cart(id),
    height DOUBLE PRECISION NOT NULL,
    visible_points INTEGER NOT NULL,
    total_points INTEGER NOT NULL,
    coverage_ratio DOUBLE PRECISION NOT NULL,
    max_visible_distance DOUBLE PRECISION NOT NULL,
    visible_grid JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_sensor_data_cart_time ON sensor_data(cart_id, timestamp DESC);
CREATE INDEX idx_sensor_data_timestamp ON sensor_data(timestamp DESC);
CREATE INDEX idx_alert_record_cart_time ON alert_record(cart_id, created_at DESC);
CREATE INDEX idx_alert_record_type ON alert_record(alert_type);
CREATE INDEX idx_alert_record_unacked ON alert_record(acknowledged) WHERE acknowledged = FALSE;
CREATE INDEX idx_terrain_region ON terrain_elevation(region_name, grid_x, grid_y);
CREATE INDEX idx_vision_cart_height ON vision_analysis_result(cart_id, height);

INSERT INTO nest_cart (id, name, description, boom_length, boom_cross_section_area, boom_moment_of_inertia, boom_elastic_modulus, basket_weight, base_height, max_height, stress_limit, sway_limit)
VALUES
    ('a1b2c3d4-e5f6-7890-abcd-ef1234567890', '巢车一号', '春秋时期复原巢车，主悬臂8米，吊篮承重150kg', 8.0, 0.01, 8.33e-6, 1.2e10, 150.0, 4.0, 15.0, 8.0e6, 0.5),
    ('b2c3d4e5-f6a7-8901-bcde-f12345678901', '巢车二号', '战国改进型巢车，加强悬臂10米，吊篮承重200kg', 10.0, 0.015, 1.25e-5, 1.2e10, 200.0, 5.0, 18.0, 1.0e7, 0.4);

INSERT INTO terrain_elevation (grid_x, grid_y, elevation, resolution, region_name)
SELECT
    x, y,
    50.0 + 30.0 * sin(radians(x * 3.6)) * cos(radians(y * 3.6))
     + 15.0 * sin(radians(x * 7.2 + 30)) * cos(radians(y * 5.4 + 45))
     + 5.0 * random(),
    10.0,
    'default_battlefield'
FROM generate_series(0, 99) AS x
CROSS JOIN generate_series(0, 99) AS y;
