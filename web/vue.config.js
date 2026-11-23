module.exports = {
  publicPath: './',
  devServer: { host: '127.0.0.1', proxy: { '/api': { target: 'http://127.0.0.1:8780' } } }
}
