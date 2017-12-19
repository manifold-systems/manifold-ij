class MyJsClass {
  constructor() {
    this._foo = "later"
  }

  hi() {
  }

  get Yeah() {
    return this._foo
  }
  set Yeah2( value ) {
    this._foo = value
  }
}