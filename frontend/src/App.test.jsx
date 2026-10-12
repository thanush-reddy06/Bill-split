import {beforeEach,afterEach,test,expect,vi} from 'vitest';
import {render,screen,cleanup,waitFor,within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
let records,requests,loggedIn;
const profile={id:1,name:'Thanush',email:'thanush@example.com'};
const settlement={mode:'SETTLE',unpaidCents:0,totalCents:300000,participants:[
 {name:'Thanush',paidCents:200000,shareCents:100000,balanceCents:100000},
 {name:'Arjun',paidCents:100000,shareCents:100000,balanceCents:0},
 {name:'Sai',paidCents:0,shareCents:100000,balanceCents:-100000}],
 transfers:[{from:'Sai',to:'Thanush',amountCents:100000}]};
const equal={mode:'EQUAL',unpaidCents:10000,totalCents:10000,participants:[
 {name:'Thanush',paidCents:0,shareCents:3334,balanceCents:-3334},
 {name:'Arjun',paidCents:0,shareCents:3333,balanceCents:-3333},
 {name:'Sai',paidCents:0,shareCents:3333,balanceCents:-3333}],transfers:[]};
const response=(body,status=200)=>new Response(JSON.stringify(body),{status});
beforeEach(()=>{
 records=[];requests=[];loggedIn=true;window.location.hash='/login';
 vi.stubGlobal('confirm',vi.fn(()=>true));
 vi.stubGlobal('fetch',vi.fn(async(path,options={})=>{
  const method=options.method||'GET';
  const data=options.body?(options.headers?.['Content-Type']==='application/x-www-form-urlencoded'?Object.fromEntries(new URLSearchParams(options.body)):JSON.parse(options.body)):null;
  requests.push({path,method,data,headers:options.headers});
  if(path==='/api/auth/csrf')return response({headerName:'X-CSRF-TOKEN',token:'csrf-token'});
  if(method!=='GET'&&options.headers?.['X-CSRF-TOKEN']!=='csrf-token')return response({message:'Missing CSRF'},403);
  if(path==='/api/auth/register')return response({message:'Created'},201);
  if(path==='/api/auth/login'){if(data.password!=='password123')return response({message:'Incorrect email or password.'},401);loggedIn=true;return response({message:'Logged in'});}
  if(path==='/api/auth/logout'){loggedIn=false;return new Response(null,{status:204});}
  if(!loggedIn)return response({message:'Please log in to continue.'},401);
  if(path==='/api/auth/me')return response(profile);
  let output;
  if(path==='/api/calculate'){
   if(data.mode==='SETTLE'&&Number(data.total)!==3000)return response({message:'The amounts already paid must add up to the total expense.'},400);
   output=data.mode==='EQUAL'?equal:settlement;
  }else if(path==='/api/trips'&&method==='GET')output=records;
  else if(path==='/api/trips'&&method==='POST'){
   output={id:1,version:0,title:data.title,currency:data.currency,settled:false,createdAt:'2026-09-22T00:00:00Z',calculation:data.mode==='EQUAL'?equal:settlement};records=[output];
  }else if(path==='/api/trips/1'&&method==='GET')output=records[0];
  else if(path==='/api/trips/1/status'){output={...records[0],settled:data.settled,version:records[0].version+1};records=[output];}
  else if(path==='/api/trips/1'&&method==='PUT'){output={...records[0],title:data.title,version:records[0].version+1,settled:false};records=[output];}
  else if(method==='DELETE'){records=[];return new Response(null,{status:204});}
  else throw new Error('Unexpected request '+method+' '+path);
  return response(output);
 }));
});
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
test('registration, password validation, login redirect and logout',async()=>{
 loggedIn=false;const user=userEvent.setup();render(<App/>);
 await user.click(await screen.findByRole('link',{name:'Register'}));
 await user.type(await screen.findByLabelText('Your name'),'Thanush');
 await user.type(screen.getByLabelText('Email'),'thanush@example.com');
 await user.type(screen.getByLabelText('Password',{exact:true}),'password123');
 await user.type(screen.getByLabelText('Confirm password'),'different');
 await user.click(screen.getByRole('button',{name:'Register'}));
 expect(await screen.findByRole('alert')).toHaveTextContent('Passwords do not match');
 expect(requests.some(r=>r.path==='/api/auth/register')).toBe(false);
 await user.clear(screen.getByLabelText('Confirm password'));await user.type(screen.getByLabelText('Confirm password'),'password123');
 await user.click(screen.getByRole('button',{name:'Register'}));
 expect(await screen.findByRole('status')).toHaveTextContent('Account created');
 await screen.findByRole('heading',{name:'Welcome back'});
 await user.type(screen.getByLabelText('Password',{exact:true}),'wrong');
 await user.click(screen.getByRole('button',{name:'Log in'}));
 expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect email or password');
 expect(screen.queryByRole('heading',{name:'Split a bill'})).not.toBeInTheDocument();
 await user.clear(screen.getByLabelText('Password',{exact:true}));await user.type(screen.getByLabelText('Password',{exact:true}),'password123');
 await user.click(screen.getByRole('button',{name:'Log in'}));
 await screen.findByRole('heading',{name:'Split a bill'});
 await waitFor(()=>expect(window.location.hash).toBe('#/app'));
 await user.click(screen.getByRole('button',{name:'Log out'}));
 await screen.findByRole('heading',{name:'Welcome back'});
 expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
},20000);
test('zero payer result, save, history, settlement, edit and delete',async()=>{
 const user=userEvent.setup();render(<App/>);
 await user.click(await screen.findByRole('button',{name:'Try example'}));
 await user.click(screen.getByRole('button',{name:'Calculate split'}));
 await screen.findByRole('heading',{name:'Who pays whom'});
 const sai=screen.getByRole('row',{name:/Sai/});
 expect(within(sai).getByText('₹0.00')).toBeInTheDocument();
 expect(within(sai).getByText('Pays ₹1,000.00')).toBeInTheDocument();
 expect(screen.getByRole('listitem')).toHaveTextContent('Sai pays Thanush ₹1,000.00');
 expect(records).toHaveLength(0);
 await user.click(screen.getByRole('button',{name:'Save split to history'}));
 await screen.findByRole('button',{name:'Mark all as settled'});
 await user.click(screen.getByRole('button',{name:/My history/}));
 await user.click(await screen.findByRole('button',{name:/Dinner with friends/}));
 await user.click(await screen.findByRole('button',{name:'Mark all as settled'}));
 await screen.findByRole('heading',{name:'Settled'});expect(records[0].settled).toBe(true);
 await user.click(screen.getByRole('button',{name:'Edit',exact:true}));
 const title=screen.getByLabelText('Trip or dining name');await user.clear(title);await user.type(title,'Dinner edited');
 await user.click(screen.getByRole('button',{name:'Calculate split'}));
 await user.click(await screen.findByRole('button',{name:'Save changes'}));
 await screen.findByRole('heading',{name:'Dinner edited'});
 expect(records[0].settled).toBe(false);expect(requests.find(r=>r.method==='PUT').data.version).toBe(1);
 await user.click(screen.getByRole('button',{name:'Delete split'}));
 await screen.findByRole('heading',{name:'My saved bills'});expect(records).toHaveLength(0);
},20000);
test('equal split uses zero paid, shows exact shares and saves correct mode',async()=>{
 const user=userEvent.setup();render(<App/>);
 await user.click(await screen.findByRole('button',{name:'Try example'}));
 await user.selectOptions(screen.getByLabelText('Split type'),'EQUAL');
 const total=screen.getByLabelText('Total expense');await user.clear(total);await user.type(total,'100');
 expect(screen.queryByLabelText('Amount paid by person 1')).not.toBeInTheDocument();
 await user.click(screen.getByRole('button',{name:'Calculate split'}));
 await screen.findByRole('heading',{name:'Each person’s share'});
 expect(screen.getAllByRole('listitem')).toHaveLength(3);
 expect(screen.getAllByRole('listitem')[0]).toHaveTextContent('Thanush pays ₹33.34 toward the bill.');
 expect(screen.queryByText(/No repayments needed/)).not.toBeInTheDocument();
 expect(requests.find(r=>r.path==='/api/calculate').data.participants.every(p=>p.paid==='0')).toBe(true);
 await user.click(screen.getByRole('button',{name:'Save split to history'}));
 await screen.findByRole('button',{name:'Mark all as settled'});expect(records[0].calculation.mode).toBe('EQUAL');
});
test('editing clears stale results and displays backend validation',async()=>{
 const user=userEvent.setup();render(<App/>);
 await user.click(await screen.findByRole('button',{name:'Try example'}));
 await user.click(screen.getByRole('button',{name:'Calculate split'}));await screen.findByRole('button',{name:'Save split to history'});
 const total=screen.getByLabelText('Total expense');await user.clear(total);await user.type(total,'4000');
 expect(screen.queryByRole('button',{name:'Save split to history'})).not.toBeInTheDocument();
 await user.click(screen.getByRole('button',{name:'Calculate split'}));
 expect(await screen.findByRole('alert')).toHaveTextContent('must add up');expect(records).toHaveLength(0);
});
test('expired sessions remove account screens and return to login',async()=>{
 const user=userEvent.setup();render(<App/>);
 await user.click(await screen.findByRole('button',{name:'Try example'}));loggedIn=false;
 await user.click(screen.getByRole('button',{name:'Calculate split'}));
 await screen.findByRole('heading',{name:'Welcome back'});
 expect(screen.queryByRole('navigation')).not.toBeInTheDocument();expect(await screen.findByRole('alert')).toHaveTextContent('session ended');
});
