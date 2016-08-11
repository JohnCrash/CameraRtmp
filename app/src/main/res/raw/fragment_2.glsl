precision mediump float;
uniform sampler2D texture;
varying vec2 v_TexCoordinate;

void main () {
    vec3 rgb;
    rgb.r = texture2D(texture, v_TexCoordinate).r;
    rgb.g = rgb.r;
    rgb.b = rgb.r;
    gl_FragColor = vec4(rgb,1);
}