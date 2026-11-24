module.exports = {
  publicPath: './',
  chainWebpack: config => {
    config.module.rule('images').set('parser', {
      dataUrlCondition: { maxSize: 4 * 1024 }
    })
  },
  devServer: { host: '127.0.0.1', proxy: { '/api': { target: 'http://127.0.0.1:8788' } } }
}
