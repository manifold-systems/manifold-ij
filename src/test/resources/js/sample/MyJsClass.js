class MyJsClass {
  constructor() {
    this._foo = "later"
  }

  hi() {
  }

  get Yeah() {
    return this._foo
  }
  set Yeah( value ) {
    this._foo = value
  }
}