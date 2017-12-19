class MyJsClass {
  constructor() {
    this._foo = "later"
  }

  hi() {
  }

  get Yeah2() {
    return this._foo
  }
  set Yeah( value ) {
    this._foo = value
  }
}